package com.ikeralvaro.phonecleaner.feature.media

import com.ikeralvaro.phonecleaner.ui.icons.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionSnapshot
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.ui.components.CategoryRow
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import com.ikeralvaro.phonecleaner.ui.components.EmptyState
import com.ikeralvaro.phonecleaner.ui.components.GradientHeader
import com.ikeralvaro.phonecleaner.ui.components.listPadding
import androidx.compose.foundation.layout.WindowInsets

import com.ikeralvaro.phonecleaner.ui.components.SectionCard

@Composable
fun MediaScreen(
    viewModel: MediaViewModel,
    onOpenCategory: (MediaCategory) -> Unit,
    onOpenCompress: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions by rememberPermissionSnapshot()
    val actions = rememberPermissionActions()
    LaunchedEffect(permissions.media) { if (permissions.media) viewModel.analyzeIfNeeded() }
    MediaContent(
        state = state,
        hasAccess = permissions.media,
        onBack = onBack,
        onGrantMedia = actions.requestMedia,
        onRefresh = viewModel::analyze,
        onOpenCategory = onOpenCategory,
        onOpenCompress = onOpenCompress,
    )
}

@Composable
fun MediaContent(
    state: MediaUiState,
    hasAccess: Boolean,
    onBack: (() -> Unit)?,
    onGrantMedia: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCategory: (MediaCategory) -> Unit,
    onOpenCompress: () -> Unit,
) {
    Scaffold(
        topBar = {
            GradientHeader(
                stringResource(R.string.files_title),
                onBack = onBack,
                actions = {
                    if (state.phase == MediaPhase.READY) {
                        IconButton(onClick = onRefresh) {
                            Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(listPadding(padding)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasAccess || state.phase == MediaPhase.NO_ACCESS) {
                PermissionCard(stringResource(R.string.perm_media_title), stringResource(R.string.perm_media_desc), onGrantMedia)
            }
            val nothingFound = state.phase == MediaPhase.READY && MediaCategory.entries.all { state.count(it) == 0 }
            if (nothingFound) {
                SectionCard {
                    EmptyState(
                        AppIcons.TaskAlt,
                        stringResource(R.string.files_nothing_found),
                        stringResource(R.string.files_nothing_found_desc),
                    )
                }
            }
            if (state.phase == MediaPhase.ANALYZING) {
                SectionCard {
                    Text(stringResource(R.string.files_analyzing), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            SectionCard {
                MediaCategory.entries.forEach { category ->
                    val ready = state.phase == MediaPhase.READY
                    val count = state.count(category)
                    CategoryRow(
                        title = stringResource(category.title()),
                        subtitle = if (ready) {
                            stringResource(R.string.join_dot, countText(category, count), stringResource(category.description()))
                        } else {
                            stringResource(category.description())
                        },
                        trailing = if (ready) formatBytes(state.bytes(category)) else "",
                        icon = category.icon(),
                        onClick = if (ready) ({ onOpenCategory(category) }) else null,
                    )
                }
            }
            SectionCard {
                CategoryRow(
                    title = stringResource(R.string.files_compress),
                    subtitle = stringResource(R.string.files_compress_desc),
                    trailing = "",
                    icon = AppIcons.Compress,
                    onClick = onOpenCompress,
                )
            }
        }
    }
}

@Composable
private fun countText(category: MediaCategory, count: Int): String = when (category) {
    MediaCategory.DUPLICATES, MediaCategory.SIMILAR -> pluralStringResource(R.plurals.groups_count, count, count)
    else -> pluralStringResource(R.plurals.files_count, count, count)
}

fun MediaCategory.title(): Int = when (this) {
    MediaCategory.DUPLICATES -> R.string.files_duplicates
    MediaCategory.SIMILAR -> R.string.files_similar
    MediaCategory.SCREENSHOTS -> R.string.files_screenshots
    MediaCategory.LARGE_VIDEOS -> R.string.files_large_videos
    MediaCategory.LARGE_FILES -> R.string.files_large_files
}

fun MediaCategory.description(): Int = when (this) {
    MediaCategory.DUPLICATES -> R.string.files_duplicates_desc
    MediaCategory.SIMILAR -> R.string.files_similar_desc
    MediaCategory.SCREENSHOTS -> R.string.files_screenshots_desc
    MediaCategory.LARGE_VIDEOS -> R.string.files_large_videos_desc
    MediaCategory.LARGE_FILES -> R.string.files_large_files_desc
}

fun MediaCategory.icon(): ImageVector = when (this) {
    MediaCategory.DUPLICATES -> AppIcons.ContentCopy
    MediaCategory.SIMILAR -> AppIcons.BurstMode
    MediaCategory.SCREENSHOTS -> AppIcons.Screenshot
    MediaCategory.LARGE_VIDEOS -> AppIcons.VideoFile
    MediaCategory.LARGE_FILES -> AppIcons.FolderZip
}
