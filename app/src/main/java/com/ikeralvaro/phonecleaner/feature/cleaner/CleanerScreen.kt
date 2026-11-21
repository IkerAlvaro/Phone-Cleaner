package com.ikeralvaro.phonecleaner.feature.cleaner

import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionSnapshot
import com.ikeralvaro.phonecleaner.core.permissions.startSafely
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.ui.components.AppIcon
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.IconBadge
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.components.UsageRing

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
        viewModel.onAppCacheCleared()
    }

    CleanerContent(
        state = state,
        hasFilesAccess = permissions.allFiles,
        hasUsageAccess = permissions.usage,
        onBack = onBack,
        onScan = viewModel::startScan,
        onGrantFiles = actions.requestAllFiles,
        onGrantUsage = actions.requestUsage,
        onToggleCategory = viewModel::toggleCategory,
        onToggleItem = viewModel::toggleItem,
        onToggleExpanded = viewModel::toggleExpanded,
        onClean = viewModel::clean,
        onClearAllCaches = {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    clearCacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
                } catch (_: Exception) {
                    actions.requestAllFiles()
                }
            }
        },
        onOpenApp = { pkg -> context.startSafely(manager.appDetailsIntent(pkg)) },
    )
}

@Composable
fun CleanerContent(
    state: CleanerUiState,
    hasFilesAccess: Boolean,
    hasUsageAccess: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onGrantFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onToggleCategory: (JunkCategory) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleExpanded: (JunkCategory) -> Unit,
    onClean: () -> Unit,
    onClearAllCaches: () -> Unit,
    onOpenApp: (String) -> Unit,
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    Scaffold(
        topBar = { PhoneCleanerTopBar(stringResource(R.string.cleaner_title), onBack = onBack) },
        bottomBar = {
            when (state.phase) {
                CleanerPhase.RESULTS -> BottomAction {
                    BigActionButton(
                        text = if (state.selectedBytes > 0 || state.selectedItems.isNotEmpty()) {
                            stringResource(R.string.cleaner_clean_button, formatBytes(state.selectedBytes))
                        } else {
                            stringResource(R.string.cleaner_nothing_selected)
                        },
                        onClick = onClean,
                        enabled = state.selectedItems.isNotEmpty(),
                        icon = Icons.Rounded.CleaningServices,
                    )
                }
                CleanerPhase.DONE -> BottomAction {
                    BigActionButton(stringResource(R.string.cleaner_back_home), onBack)
                }
                CleanerPhase.IDLE -> BottomAction {
                    BigActionButton(stringResource(R.string.cleaner_start), onScan, icon = Icons.Rounded.CleaningServices)
                }
                else -> Unit
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.phase) {
                CleanerPhase.IDLE -> IdleView(hasFilesAccess, hasUsageAccess, onGrantFiles, onGrantUsage)
                CleanerPhase.SCANNING -> ProgressView(
                    title = stringResource(R.string.cleaner_scanning),
                    bytes = state.progress?.bytesFound,
                    detail = state.progress?.let { pluralStringResource(R.plurals.files_count, it.filesScanned, it.filesScanned) },
                    path = state.progress?.currentPath,
                )
                CleanerPhase.CLEANING -> ProgressView(stringResource(R.string.cleaner_cleaning), state.selectedBytes, null, null)
                CleanerPhase.RESULTS -> ResultsView(
                    state, hasFilesAccess, hasUsageAccess, onGrantFiles, onGrantUsage, onToggleCategory, onToggleItem,
                    onToggleExpanded, onClearAllCaches, onOpenApp, sdkInt,
                )
                CleanerPhase.DONE -> DoneView(state.freedBytes)
            }
        }
    }
}

@Composable
private fun BottomAction(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp),
        ) { content() }
    }
}

@Composable
private fun IdleView(hasFiles: Boolean, hasUsage: Boolean, onGrantFiles: () -> Unit, onGrantUsage: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(Icons.Rounded.CleaningServices, Modifier.size(88.dp))
        Text(
            stringResource(R.string.cleaner_intro),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (!hasFiles) PermissionCard(stringResource(R.string.perm_files_title), stringResource(R.string.perm_files_desc), onGrantFiles)
        if (!hasUsage) PermissionCard(stringResource(R.string.perm_usage_title), stringResource(R.string.perm_usage_desc), onGrantUsage)
    }
}

@Composable
private fun ProgressView(title: String, bytes: Long?, detail: String?, path: String?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
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
    hasUsage: Boolean,
    onGrantFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onToggleCategory: (JunkCategory) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleExpanded: (JunkCategory) -> Unit,
    onClearAllCaches: () -> Unit,
    onOpenApp: (String) -> Unit,
    sdkInt: Int,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
        item { AppCacheCard(state, hasFiles, hasUsage, onGrantFiles, onGrantUsage, onClearAllCaches, onOpenApp, sdkInt) }
        if (!hasFiles) {
            item {
                PermissionCard(
                    stringResource(R.string.perm_files_title),
                    stringResource(R.string.cleaner_needs_files),
                    onGrantFiles,
                )
            }
        }
        if (state.groups.isNotEmpty()) {
            item {
                SectionCard {
                    state.groups.forEachIndexed { index, group ->
                        JunkGroupRow(
                            group = group,
                            selectedPaths = state.selectedPaths,
                            expanded = group.category in state.expanded,
                            onToggleCategory = { onToggleCategory(group.category) },
                            onToggleItem = onToggleItem,
                            onToggleExpanded = { onToggleExpanded(group.category) },
                        )
                        if (index < state.groups.lastIndex) Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun JunkGroupRow(
    group: JunkGroup,
    selectedPaths: Set<String>,
    expanded: Boolean,
    onToggleCategory: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val selectedCount = group.items.count { it.path in selectedPaths }
    val toggleState = when (selectedCount) {
        0 -> ToggleableState.Off
        group.items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggleExpanded)
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
                    pluralStringResource(R.plurals.items_count, group.items.size, group.items.size) + " · " +
                        formatBytes(group.totalBytes),
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
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TriStateCheckbox(state = toggleState, onClick = onToggleCategory)
        }
        if (expanded) {
            group.items.take(MAX_VISIBLE_ITEMS).forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
                    Checkbox(checked = item.path in selectedPaths, onCheckedChange = { onToggleItem(item.path) })
                }
            }
            if (group.items.size > MAX_VISIBLE_ITEMS) {
                Text(
                    "+${group.items.size - MAX_VISIBLE_ITEMS}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
                )
            }
        }
    }
}

private const val MAX_VISIBLE_ITEMS = 50

@Composable
private fun AppCacheCard(
    state: CleanerUiState,
    hasFiles: Boolean,
    hasUsage: Boolean,
    onGrantFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onClearAllCaches: () -> Unit,
    onOpenApp: (String) -> Unit,
    sdkInt: Int,
) {
    if (!hasUsage || state.appCache == null) {
        PermissionCard(stringResource(R.string.perm_usage_title), stringResource(R.string.app_cache_needs_usage), onGrantUsage)
        return
    }
    val cache = state.appCache
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Layers)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_cache_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    pluralStringResource(R.plurals.apps_count, cache.apps.size, cache.apps.size) + " · " +
                        stringResource(R.string.app_cache_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatBytes(cache.totalBytes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
            sdkInt >= 30 && hasFiles -> Button(onClick = onClearAllCaches, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.app_cache_clear_all))
            }
            sdkInt >= 30 -> {
                Text(stringResource(R.string.app_cache_needs_files), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGrantFiles, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_grant)) }
            }
            else -> {
                Text(stringResource(R.string.app_cache_per_app), style = MaterialTheme.typography.bodySmall)
                cache.apps.take(8).forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.packageName, app.label, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatBytes(app.cacheBytes ?: 0L), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onOpenApp(app.packageName) }) { Text(stringResource(R.string.action_open_settings)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneView(freedBytes: Long) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        UsageRing(progress = 1f, size = 200.dp) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.cleaner_done_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (freedBytes > 0) stringResource(R.string.cleaner_done_freed, formatBytes(freedBytes)) else stringResource(R.string.cleaner_done_clean),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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
    JunkCategory.THUMBNAILS -> Icons.Rounded.Image
    JunkCategory.TEMP_FILES -> Icons.Rounded.Description
    JunkCategory.EMPTY_FOLDERS -> Icons.Rounded.FolderOff
    JunkCategory.APK_INSTALLED, JunkCategory.APK_OTHER -> Icons.Rounded.Android
    JunkCategory.OLD_DOWNLOADS -> Icons.Rounded.Download
    JunkCategory.RESIDUAL_FOLDERS -> Icons.Rounded.DeleteSweep
}
