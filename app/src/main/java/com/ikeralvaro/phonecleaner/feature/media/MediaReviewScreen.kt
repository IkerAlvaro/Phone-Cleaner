package com.ikeralvaro.phonecleaner.feature.media

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.EmptyState
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.Tag
import kotlinx.coroutines.launch

@Composable
fun MediaReviewScreen(viewModel: MediaViewModel, category: MediaCategory, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable(category) { mutableStateOf(state.defaultSelection(category).toLongArray()) }
    var pending by remember { mutableStateOf<DeleteRequest.NeedsConfirmation?>(null) }
    val scope = rememberCoroutineScope()

    val confirmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val request = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleted(request.pendingIds, request.freedSoFar)
            selected = selected.filter { it !in request.pendingIds }.toLongArray()
        }
    }

    MediaReviewContent(
        state = state,
        category = category,
        selected = selected.toSet(),
        onBack = onBack,
        onToggle = { id ->
            selected = if (id in selected) selected.filter { it != id }.toLongArray() else selected + id
        },
        onSelectAll = { all -> selected = if (all) state.items(category).map { it.id }.toLongArray() else LongArray(0) },
        onDelete = {
            val ids = selected.toSet()
            scope.launch {
                when (val request = viewModel.prepareDelete(ids)) {
                    is DeleteRequest.NeedsConfirmation -> {
                        if (request.freedSoFar > 0) viewModel.onDeleted(emptySet(), request.freedSoFar)
                        pending = request
                        confirmLauncher.launch(IntentSenderRequest.Builder(request.sender).build())
                    }
                    is DeleteRequest.Done -> {
                        viewModel.onDeleted(request.deletedIds, request.freedBytes)
                        selected = selected.filter { it !in request.deletedIds }.toLongArray()
                    }
                }
            }
        },
    )
}

@Composable
fun MediaReviewContent(
    state: MediaUiState,
    category: MediaCategory,
    selected: Set<Long>,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val items = state.items(category)
    val selectedBytes = items.filter { it.id in selected }.sumOf { it.sizeBytes }
    val allSelected = items.isNotEmpty() && items.all { it.id in selected }
    Scaffold(
        topBar = {
            PhoneCleanerTopBar(
                stringResource(category.title()),
                onBack = onBack,
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { onSelectAll(!allSelected) }) {
                            Text(stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        Text(
                            stringResource(if (Build.VERSION.SDK_INT >= 30) R.string.files_trash_note else R.string.files_delete_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        BigActionButton(
                            text = stringResource(R.string.files_delete_button, formatBytes(selectedBytes)),
                            onClick = onDelete,
                            enabled = selected.isNotEmpty(),
                            icon = Icons.Rounded.Delete,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.CheckCircle,
                stringResource(R.string.files_empty),
                stringResource(R.string.files_empty_desc),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.lastDeletedBytes?.let { deleted ->
                item(key = "deleted", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.files_deleted, formatBytes(deleted)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val groups = state.groups(category)
            if (groups != null) {
                groups.forEachIndexed { index, group ->
                    item(key = "header-$index", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${index + 1} · ${formatBytes(group.items.sumOf { it.sizeBytes })}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(group.items, key = { it.id }) { item ->
                        MediaTile(item, item.id in selected, keep = item.id == group.keeperId) { onToggle(item.id) }
                    }
                }
            } else {
                items(items, key = { it.id }) { item ->
                    MediaTile(item, item.id in selected, keep = false) { onToggle(item.id) }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, selected: Boolean, keep: Boolean, onToggle: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .border(3.dp, borderColor, MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onToggle),
    ) {
        if (item.isImage || item.isVideo) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        if (keep) {
            Tag(
                stringResource(R.string.files_keep_badge),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                background = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
