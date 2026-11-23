package com.ikeralvaro.phonecleaner.feature.compress

import com.ikeralvaro.phonecleaner.ui.icons.AppIcons
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.ads.AdPlacement
import com.ikeralvaro.phonecleaner.core.ads.NativeAdCard
import com.ikeralvaro.phonecleaner.core.ads.rememberFinishWithAd
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.EmptyState
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import com.ikeralvaro.phonecleaner.ui.components.BottomAction
import com.ikeralvaro.phonecleaner.ui.components.GradientHeader
import com.ikeralvaro.phonecleaner.ui.components.SecondaryButton
import com.ikeralvaro.phonecleaner.ui.components.listPadding
import com.ikeralvaro.phonecleaner.ui.theme.cardBorderColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.remember
import com.ikeralvaro.phonecleaner.ui.components.SectionCard

@Composable
fun CompressScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { CompressViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberPermissionActions()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    val finishWithAd = rememberFinishWithAd()
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onOriginalsTrashed(it.resultCode == Activity.RESULT_OK)
    }
    val askTrash = {
        viewModel.trashOriginalsRequest()?.let { trashLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        Unit
    }
    // Al terminar con la pantalla abierta, se ofrece una vez mover los originales a la papelera
    // (el sistema pide confirmación). Si se rechaza, queda el botón en la tarjeta de resultado.
    LaunchedEffect(state.justFinished) {
        if (state.justFinished) {
            viewModel.consumeJustFinished()
            if (state.run.originals.isNotEmpty() && !state.run.originalsTrashed) askTrash()
        }
    }

    CompressContent(
        state = state,
        onBack = onBack,
        onGrantMedia = actions.requestMedia,
        onTab = viewModel::setTab,
        onMode = viewModel::setMode,
        onToggle = viewModel::toggle,
        onSelectAll = viewModel::selectAllVisible,
        onStart = viewModel::start,
        onTrashOriginals = askTrash,
        // El anuncio solo sale con el botón explícito "Hecho"; cerrar un error no lo muestra.
        onDismissResult = { finishWithAd(viewModel::dismissResult) },
        onRetry = {
            viewModel.dismissResult()
            viewModel.start()
        },
        onCloseFailure = viewModel::dismissResult,
    )
}

@Composable
fun CompressContent(
    state: CompressUiState,
    onBack: () -> Unit,
    onGrantMedia: () -> Unit,
    onTab: (CompressTab) -> Unit,
    onMode: (CompressionMode) -> Unit,
    onToggle: (Long) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onStart: () -> Unit,
    onTrashOriginals: () -> Unit,
    onDismissResult: () -> Unit,
    sdkInt: Int = Build.VERSION.SDK_INT,
    onRetry: () -> Unit = onStart,
    onCloseFailure: () -> Unit = onDismissResult,
) {
    val visible = state.visible
    val allVisibleSelected = remember(visible, state.selected) { visible.isNotEmpty() && visible.all { it.item.id in state.selected } }
    val savings = remember(state.photos, state.videos, state.selected) { state.estimatedSavings }
    Scaffold(
        topBar = {
            GradientHeader(
                stringResource(R.string.compress_title),
                onBack = onBack,
                actions = {
                    if (visible.isNotEmpty() && state.run.state != RunState.RUNNING) {
                        TextButton(
                            onClick = { onSelectAll(!allVisibleSelected) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Text(stringResource(if (allVisibleSelected) R.string.action_deselect_all else R.string.action_select_all))
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (state.run.state != RunState.RUNNING && state.selected.isNotEmpty()) {
                BottomAction {
                    BigActionButton(
                        text = stringResource(R.string.compress_button, state.selected.size, formatBytes(savings)),
                        onClick = onStart,
                        icon = AppIcons.Compress,
                    )
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding(padding, horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
            if (!state.hasAccess) {
                item(key = "permission", span = full, contentType = "card") {
                    PermissionCard(stringResource(R.string.perm_media_title), stringResource(R.string.perm_media_desc), onGrantMedia)
                }
            }
            item(key = "run", span = full, contentType = "card") {
                RunCard(state.run, sdkInt, onTrashOriginals, onDismissResult, onRetry, onCloseFailure)
            }
            if (state.run.state == RunState.FINISHED) {
                item(key = "ad", span = full, contentType = "ad") { NativeAdCard(AdPlacement.RESULT) }
            }
            item(key = "tabs", span = full, contentType = "tabs") {
                SecondaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(
                        selected = state.tab == CompressTab.PHOTOS,
                        onClick = { onTab(CompressTab.PHOTOS) },
                        text = { Text(stringResource(R.string.compress_photos_count, state.photos.size)) },
                    )
                    Tab(
                        selected = state.tab == CompressTab.VIDEOS,
                        onClick = { onTab(CompressTab.VIDEOS) },
                        text = { Text(stringResource(R.string.compress_videos_count, state.videos.size)) },
                    )
                }
            }
            item(key = "mode", span = full, contentType = "mode") { ModeSelector(state.mode, onMode) }
            item(key = "explain", span = full, contentType = "explain") {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Icon(AppIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.compress_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.loading && visible.isEmpty() && state.hasAccess) {
                item(key = "loading", span = full, contentType = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }
            if (!state.loading && visible.isEmpty() && state.hasAccess) {
                item(key = "empty", span = full, contentType = "empty") {
                    EmptyState(AppIcons.TaskAlt, stringResource(R.string.files_empty), stringResource(R.string.compress_empty))
                }
            }
            items(visible, key = { it.item.id }, contentType = { "tile" }) { candidate ->
                CandidateTile(candidate, candidate.item.id in state.selected) { onToggle(candidate.item.id) }
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: CompressionMode, onMode: (CompressionMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(CompressionMode.HIGH_QUALITY, R.string.compress_mode_high, R.string.compress_mode_high_desc),
            Triple(CompressionMode.MAX_SAVINGS, R.string.compress_mode_max, R.string.compress_mode_max_desc),
        ).forEach { (value, title, desc) ->
            val selected = mode == value
            Card(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, onClick = { onMode(value) }, role = Role.RadioButton),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else cardBorderColor(),
                ),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCard(
    run: CompressRun,
    sdkInt: Int,
    onTrashOriginals: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onCloseFailure: () -> Unit,
) {
    when (run.state) {
        RunState.IDLE -> Unit
        RunState.RUNNING -> SectionCard {
            Text(
                stringResource(R.string.compress_running, (run.done + 1).coerceAtMost(run.total.coerceAtLeast(1)), run.total),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (run.total > 0) run.done.toFloat() / run.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            if (run.savedBytes > 0) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.compress_done, formatBytes(run.savedBytes)), style = MaterialTheme.typography.bodySmall)
            }
        }
        RunState.FINISHED -> SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (run.savedBytes > 0) stringResource(R.string.compress_done, formatBytes(run.savedBytes)) else stringResource(R.string.compress_done_none),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (run.originals.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                when {
                    run.originalsTrashed -> Text(stringResource(R.string.compress_originals_trashed), style = MaterialTheme.typography.bodyMedium)
                    sdkInt >= 30 -> SecondaryButton(stringResource(R.string.compress_trash_originals), onTrashOriginals, icon = AppIcons.Delete)
                    else -> Text(stringResource(R.string.compress_originals_kept), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            BigActionButton(stringResource(R.string.action_done), onDismiss)
        }
        RunState.FAILED -> SectionCard {
            Text(stringResource(R.string.compress_failed), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(stringResource(R.string.action_close), onCloseFailure, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.action_retry), onRetry, Modifier.weight(1f), icon = AppIcons.Refresh)
            }
        }
    }
}

@Composable
private fun CandidateTile(candidate: CompressCandidate, selected: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .border(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        AsyncImage(
            model = candidate.item.uri,
            contentDescription = candidate.item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            if (selected) AppIcons.CheckCircle else AppIcons.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f)),
        )
        Text(
            stringResource(R.string.compress_estimate, formatBytes(candidate.item.sizeBytes), formatBytes(candidate.estimatedBytes)),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
