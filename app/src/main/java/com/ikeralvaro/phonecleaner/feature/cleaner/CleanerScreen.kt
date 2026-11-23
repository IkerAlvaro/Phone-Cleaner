package com.ikeralvaro.phonecleaner.feature.cleaner

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.ads.AdPlacement
import com.ikeralvaro.phonecleaner.core.ads.NativeAdCard
import com.ikeralvaro.phonecleaner.core.ads.rememberFinishWithAd
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionSnapshot
import com.ikeralvaro.phonecleaner.core.permissions.startSafely
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.BottomAction
import com.ikeralvaro.phonecleaner.ui.components.ConfirmDialog
import com.ikeralvaro.phonecleaner.ui.components.GradientHeader
import com.ikeralvaro.phonecleaner.ui.components.IconBadge
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import com.ikeralvaro.phonecleaner.ui.components.SecondaryButton
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.components.UsageRing
import com.ikeralvaro.phonecleaner.ui.components.animatedCount
import com.ikeralvaro.phonecleaner.ui.components.bottomContentPadding
import com.ikeralvaro.phonecleaner.ui.components.listPadding
import com.ikeralvaro.phonecleaner.ui.icons.AppIcons

@Composable
fun CleanerScreen(autoStart: Boolean, onBack: () -> Unit) {
    val viewModel = appViewModel { CleanerViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions by rememberPermissionSnapshot()
    val actions = rememberPermissionActions()
    val context = LocalContext.current
    val manager = LocalAppContainer.current.permissionManager

    LaunchedEffect(autoStart) {
        if (autoStart && state.phase == CleanerPhase.IDLE) viewModel.startScan()
    }
    val clearCacheLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onAppCacheCleared(resultOk = it.resultCode == Activity.RESULT_OK)
    }
    // El anuncio de pantalla completa solo sale con el botón explícito "Volver al inicio", nunca con "atrás".
    val finishWithAd = rememberFinishWithAd()
    val busy = state.phase == CleanerPhase.SCANNING || state.phase == CleanerPhase.CLEANING
    var askCancel by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = busy) { askCancel = true }
    if (askCancel && busy) {
        CancelDialog(
            message = stringResource(
                if (state.phase == CleanerPhase.CLEANING) R.string.cleaner_cancel_cleaning else R.string.cleaner_cancel_scan,
            ),
            onConfirm = {
                askCancel = false
                // Al salir se destruye el ViewModel y con él el análisis o la limpieza en curso.
                onBack()
            },
            onDismiss = { askCancel = false },
        )
    }

    CleanerContent(
        state = state,
        hasFilesAccess = permissions.allFiles,
        onBack = { if (busy) askCancel = true else onBack() },
        onFinish = { finishWithAd(onBack) },
        onScan = viewModel::startScan,
        onGrantFiles = actions.requestAllFiles,
        onToggleCategory = viewModel::toggleCategory,
        onToggleItem = viewModel::toggleItem,
        onToggleExpanded = viewModel::toggleExpanded,
        onClean = viewModel::clean,
        onClearAllCaches = {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    viewModel.beforeAppCacheClear()
                    clearCacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
                } catch (_: Exception) {
                    actions.requestAllFiles()
                }
            }
        },
        onOpenStorageSettings = {
            if (!context.startSafely(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) {
                context.startSafely(manager.appDetailsIntent())
            }
        },
    )
}

/** "¿Cancelar?" mientras se analiza, se limpia o se libera RAM. */
@Composable
fun CancelDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.cancel_title),
        message = message,
        confirmLabel = stringResource(R.string.action_yes_cancel),
        dismissLabel = stringResource(R.string.action_continue),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

@Composable
fun CleanerContent(
    state: CleanerUiState,
    hasFilesAccess: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onGrantFiles: () -> Unit,
    onToggleCategory: (JunkCategory) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleExpanded: (JunkCategory) -> Unit,
    onClean: () -> Unit,
    onClearAllCaches: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onFinish: () -> Unit = onBack,
    sdkInt: Int = Build.VERSION.SDK_INT,
    initialConfirm: Boolean = false,
) {
    val selectedCount = remember(state.groups, state.selectedPaths) { state.selectedItems.size }
    val selectedBytes = remember(state.groups, state.selectedPaths) { state.selectedBytes }
    var confirmClean by rememberSaveable { mutableStateOf(initialConfirm) }
    if (confirmClean && state.phase == CleanerPhase.RESULTS && selectedCount > 0) {
        ConfirmDialog(
            title = stringResource(R.string.cleaner_confirm_title),
            message = pluralStringResource(R.plurals.cleaner_confirm_message, selectedCount, selectedCount, formatBytes(selectedBytes)),
            confirmLabel = stringResource(R.string.cleaner_clean_button, formatBytes(selectedBytes)),
            onConfirm = {
                confirmClean = false
                onClean()
            },
            onDismiss = { confirmClean = false },
            icon = AppIcons.CleaningServices,
            destructive = true,
        )
    }
    Scaffold(
        topBar = {
            // En "Hecho" la cabecera (con el medidor) va dentro del contenido desplazable.
            if (state.phase != CleanerPhase.DONE) GradientHeader(stringResource(R.string.cleaner_title), onBack = onBack)
        },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            when (state.phase) {
                CleanerPhase.RESULTS -> BottomAction {
                    BigActionButton(
                        text = if (selectedCount > 0) {
                            stringResource(R.string.cleaner_clean_button, formatBytes(selectedBytes))
                        } else {
                            stringResource(R.string.cleaner_nothing_selected)
                        },
                        onClick = { confirmClean = true },
                        enabled = selectedCount > 0,
                        icon = AppIcons.CleaningServices,
                    )
                }
                CleanerPhase.DONE -> BottomAction {
                    BigActionButton(stringResource(R.string.cleaner_back_home), onFinish)
                }
                CleanerPhase.IDLE -> BottomAction {
                    BigActionButton(stringResource(R.string.cleaner_start), onScan, icon = AppIcons.CleaningServices)
                }
                else -> Unit
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (state.phase) {
                CleanerPhase.IDLE -> IdleView(hasFilesAccess, onGrantFiles, padding)
                CleanerPhase.SCANNING -> ProgressView(
                    title = stringResource(R.string.cleaner_scanning),
                    bytes = state.progress?.bytesFound,
                    detail = state.progress?.let { pluralStringResource(R.plurals.files_count, it.filesScanned, it.filesScanned) },
                    path = state.progress?.currentPath,
                    padding = padding,
                )
                CleanerPhase.CLEANING -> ProgressView(stringResource(R.string.cleaner_cleaning), selectedBytes, null, null, padding)
                CleanerPhase.RESULTS -> ResultsView(
                    state, hasFilesAccess, onGrantFiles, onToggleCategory, onToggleItem,
                    onToggleExpanded, onClearAllCaches, onOpenStorageSettings, sdkInt, padding,
                )
                CleanerPhase.DONE -> DoneView(state.freedBytes, onBack, padding)
            }
        }
    }
}

@Composable
private fun IdleView(hasFiles: Boolean, onGrantFiles: () -> Unit, padding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(listPadding(padding)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(AppIcons.CleaningServices, Modifier.size(88.dp))
        Text(
            stringResource(R.string.cleaner_intro),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (!hasFiles) PermissionCard(stringResource(R.string.perm_files_title), stringResource(R.string.perm_files_desc), onGrantFiles)
    }
}

@Composable
private fun ProgressView(title: String, bytes: Long?, detail: String?, path: String?, padding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(listPadding(padding, horizontal = 24.dp, vertical = 24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(220.dp), strokeWidth = 14.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatBytes(bytes ?: 0L),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        if (detail != null) Text(detail, style = MaterialTheme.typography.titleSmall)
        if (!path.isNullOrEmpty()) {
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}

@Composable
private fun ResultsView(
    state: CleanerUiState,
    hasFiles: Boolean,
    onGrantFiles: () -> Unit,
    onToggleCategory: (JunkCategory) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleExpanded: (JunkCategory) -> Unit,
    onClearAllCaches: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    sdkInt: Int,
    padding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
        item(key = "summary", contentType = "summary") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatBytes(state.totalFoundBytes),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.cleaner_found_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "appCache", contentType = "card") {
            AppCacheCard(
                state.appCache, hasFiles, onGrantFiles, onClearAllCaches, onOpenStorageSettings, sdkInt,
                Modifier.padding(bottom = 12.dp),
            )
        }
        if (!hasFiles) {
            item(key = "permission", contentType = "card") {
                PermissionCard(
                    stringResource(R.string.perm_files_title),
                    stringResource(R.string.cleaner_needs_files),
                    onGrantFiles,
                    Modifier.padding(bottom = 12.dp),
                )
            }
        }
        // Cada cabecera de grupo y cada archivo es un elemento perezoso propio (con clave y tipo), pintados
        // como tramos de una misma tarjeta: una lista larga ya no se compone entera de golpe.
        state.groups.forEachIndexed { index, group ->
            val expanded = group.category in state.expanded
            val visible = if (expanded) group.items.take(MAX_VISIBLE_ITEMS) else emptyList()
            val hidden = if (expanded) group.items.size - visible.size else 0
            val lastGroup = index == state.groups.lastIndex
            item(key = "group:${group.category.name}", contentType = "group") {
                CardSlice(top = index == 0, bottom = lastGroup && visible.isEmpty()) {
                    JunkGroupHeader(
                        group = group,
                        selectedPaths = state.selectedPaths,
                        expanded = expanded,
                        onToggleCategory = { onToggleCategory(group.category) },
                        onToggleExpanded = { onToggleExpanded(group.category) },
                    )
                }
            }
            items(visible, key = { "item:${group.category.name}:${it.path}" }, contentType = { "item" }) { item ->
                CardSlice(top = false, bottom = lastGroup && hidden == 0 && item === visible.last()) {
                    JunkItemRow(item, item.path in state.selectedPaths) { onToggleItem(item.path) }
                }
            }
            if (hidden > 0) {
                item(key = "more:${group.category.name}", contentType = "more") {
                    CardSlice(top = false, bottom = lastGroup) {
                        Text(
                            stringResource(R.string.more_count, hidden),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Tramo de una tarjeta repartida entre varios elementos de la lista (esquinas solo arriba y/o abajo). */
@Composable
private fun CardSlice(top: Boolean, bottom: Boolean, content: @Composable () -> Unit) {
    val radius = 24.dp
    val shape = RoundedCornerShape(
        topStart = if (top) radius else 0.dp,
        topEnd = if (top) radius else 0.dp,
        bottomStart = if (bottom) radius else 0.dp,
        bottomEnd = if (bottom) radius else 0.dp,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 4.dp, top = if (top) 10.dp else 0.dp, bottom = if (bottom) 10.dp else 0.dp),
    ) { content() }
}

@Composable
private fun JunkGroupHeader(
    group: JunkGroup,
    selectedPaths: Set<String>,
    expanded: Boolean,
    onToggleCategory: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val selectedCount = group.items.count { it.path in selectedPaths }
    val toggleState = when (selectedCount) {
        0 -> ToggleableState.Off
        group.items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val expandedLabel = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    val toggleLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggleExpanded)
            .semantics { stateDescription = expandedLabel }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(group.category.icon())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(group.category.title()),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.join_dot,
                    pluralStringResource(R.plurals.items_count, group.items.size, group.items.size),
                    formatBytes(group.totalBytes),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(group.category.description()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TriStateCheckbox(state = toggleState, onClick = onToggleCategory)
    }
}

@Composable
private fun JunkItemRow(item: JunkItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 56.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    }
}

private const val MAX_VISIBLE_ITEMS = 50

@Composable
private fun AppCacheCard(
    cache: AppCacheState,
    hasFiles: Boolean,
    onGrantFiles: () -> Unit,
    onClearAllCaches: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    sdkInt: Int,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.Layers)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_cache_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.app_cache_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cache.freedBytes?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_cache_cleared, formatBytes(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            sdkInt >= 30 && hasFiles -> SecondaryButton(stringResource(R.string.app_cache_clear_all), onClearAllCaches)
            sdkInt >= 30 -> {
                Text(stringResource(R.string.app_cache_needs_files), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.action_grant), onGrantFiles)
            }
            else -> {
                Text(stringResource(R.string.app_cache_old_android), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.app_cache_storage_settings), onOpenStorageSettings)
            }
        }
    }
}

@Composable
private fun DoneView(freedBytes: Long, onBack: () -> Unit, padding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomContentPadding(padding.calculateBottomPadding())),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Como en Inicio: el medidor grande va dentro de la cabecera degradada.
        GradientHeader(stringResource(R.string.cleaner_title), onBack = onBack) {
            UsageRing(progress = 1f, size = 168.dp, color = Color.White, trackColor = Color.White.copy(alpha = 0.22f)) {
                Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cleaner_done_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                if (freedBytes > 0) {
                    stringResource(R.string.cleaner_done_freed, formatBytes(animatedCount(freedBytes)))
                } else {
                    stringResource(R.string.cleaner_done_clean)
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        NativeAdCard(AdPlacement.RESULT, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
    }
}

fun JunkCategory.title(): Int = when (this) {
    JunkCategory.THUMBNAILS -> R.string.cat_thumbnails
    JunkCategory.TEMP_FILES -> R.string.cat_temp
    JunkCategory.EMPTY_FOLDERS -> R.string.cat_empty
    JunkCategory.APK_INSTALLED -> R.string.cat_apk_installed
    JunkCategory.APK_OTHER -> R.string.cat_apk_other
    JunkCategory.OLD_DOWNLOADS -> R.string.cat_downloads
    JunkCategory.RESIDUAL_FOLDERS -> R.string.cat_residual
}

fun JunkCategory.description(): Int = when (this) {
    JunkCategory.THUMBNAILS -> R.string.cat_thumbnails_desc
    JunkCategory.TEMP_FILES -> R.string.cat_temp_desc
    JunkCategory.EMPTY_FOLDERS -> R.string.cat_empty_desc
    JunkCategory.APK_INSTALLED -> R.string.cat_apk_installed_desc
    JunkCategory.APK_OTHER -> R.string.cat_apk_other_desc
    JunkCategory.OLD_DOWNLOADS -> R.string.cat_downloads_desc
    JunkCategory.RESIDUAL_FOLDERS -> R.string.cat_residual_desc
}

private fun JunkCategory.icon(): ImageVector = when (this) {
    JunkCategory.THUMBNAILS -> AppIcons.Image
    JunkCategory.TEMP_FILES -> AppIcons.Description
    JunkCategory.EMPTY_FOLDERS -> AppIcons.FolderOff
    JunkCategory.APK_INSTALLED, JunkCategory.APK_OTHER -> AppIcons.Android
    JunkCategory.OLD_DOWNLOADS -> AppIcons.Download
    JunkCategory.RESIDUAL_FOLDERS -> AppIcons.DeleteSweep
}
