package com.ikeralvaro.phonecleaner.feature.compress

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
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
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
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
        onDismissResult = { finishWithAd(viewModel::dismissResult) },
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
) {
    val allVisibleSelected = state.visible.isNotEmpty() && state.visible.all { it.item.id in state.selected }
    Scaffold(
        topBar = {
            PhoneCleanerTopBar(
                stringResource(R.string.compress_title),
                onBack = onBack,
                actions = {
                    if (state.visible.isNotEmpty() && state.run.state != RunState.RUNNING) {
                        TextButton(onClick = { onSelectAll(!allVisibleSelected) }) {
                            Text(stringResource(if (allVisibleSelected) R.string.action_deselect_all else R.string.action_select_all))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.run.state != RunState.RUNNING && state.selected.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        BigActionButton(
                            text = stringResource(R.string.compress_button, state.selected.size, formatBytes(state.estimatedSavings)),
                            onClick = onStart,
                            icon = Icons.Rounded.Compress,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
            if (!state.hasAccess) {
                item(span = full) {
                    PermissionCard(stringResource(R.string.perm_media_title), stringResource(R.string.perm_media_desc), onGrantMedia)
                }
            }
            item(span = full) { RunCard(state.run, sdkInt, onTrashOriginals, onDismissResult) }
            if (state.run.state == RunState.FINISHED) {
                item(span = full) { NativeAdCard(AdPlacement.RESULT) }
            }
            item(span = full) {
                SecondaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(
                        selected = state.tab == CompressTab.PHOTOS,
                        onClick = { onTab(CompressTab.PHOTOS) },
                        text = { Text("${stringResource(R.string.compress_photos)} (${state.photos.size})") },
                    )
                    Tab(
                        selected = state.tab == CompressTab.VIDEOS,
                        onClick = { onTab(CompressTab.VIDEOS) },
                        text = { Text("${stringResource(R.string.compress_videos)} (${state.videos.size})") },
                    )
                }
            }
            item(span = full) { ModeSelector(state.mode, onMode) }
            item(span = full) {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.compress_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!state.loading && state.visible.isEmpty() && state.hasAccess) {
                item(span = full) { EmptyState(Icons.Rounded.TaskAlt, stringResource(R.string.files_empty), stringResource(R.string.compress_empty)) }
            }
            items(state.visible, key = { it.item.id }) { candidate ->
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
private fun RunCard(run: CompressRun, sdkInt: Int, onTrashOriginals: () -> Unit, onDismiss: () -> Unit) {
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
                Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                    sdkInt >= 30 -> Button(onClick = onTrashOriginals, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.compress_trash_originals))
                    }
                    else -> Text(stringResource(R.string.compress_originals_kept), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
        }
        RunState.FAILED -> SectionCard {
            Text(stringResource(R.string.compress_failed), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
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
            .clickable(onClick = onToggle),
    ) {
        AsyncImage(
            model = candidate.item.uri,
            contentDescription = candidate.item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
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
