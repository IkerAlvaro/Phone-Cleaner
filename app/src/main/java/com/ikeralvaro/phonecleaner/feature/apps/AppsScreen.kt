package com.ikeralvaro.phonecleaner.feature.apps

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.core.util.formatDuration
import com.ikeralvaro.phonecleaner.ui.components.AppIcon
import com.ikeralvaro.phonecleaner.ui.components.EmptyState
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.Tag
import com.ikeralvaro.phonecleaner.ui.components.rememberSequentialLauncher

private enum class SequenceMode { STOP, UNINSTALL }

fun uninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))

@Composable
fun AppsScreen(onOpenApp: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val viewModel = appViewModel { AppsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberPermissionActions()
    val manager = LocalAppContainer.current.permissionManager
    var mode by remember { mutableStateOf(SequenceMode.STOP) }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    val launchSequence = rememberSequentialLauncher(
        intentFor = { pkg -> if (mode == SequenceMode.STOP) manager.appDetailsIntent(pkg) else uninstallIntent(pkg) },
        onReturned = viewModel::onReturned,
    )

    AppsContent(
        state = state,
        onBack = onBack,
        onSort = viewModel::setSort,
        onToggle = viewModel::toggle,
        onOpenApp = onOpenApp,
        onGrantUsage = actions.requestUsage,
        onStopSelected = {
            mode = SequenceMode.STOP
            viewModel.startSequence()?.let(launchSequence)
        },
        onUninstallSelected = {
            mode = SequenceMode.UNINSTALL
            viewModel.startSequence()?.let(launchSequence)
        },
    )
}

@Composable
fun AppsContent(
    state: AppsUiState,
    onBack: (() -> Unit)?,
    onSort: (AppSort) -> Unit,
    onToggle: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onGrantUsage: () -> Unit,
    onStopSelected: () -> Unit,
    onUninstallSelected: () -> Unit,
) {
    Scaffold(
        topBar = { PhoneCleanerTopBar(stringResource(R.string.apps_title), onBack = onBack) },
        bottomBar = {
            if (state.selected.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onStopSelected, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.apps_stop_selected, state.selected.size))
                        }
                        Button(onClick = onUninstallSelected, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.apps_uninstall_selected, state.selected.size))
                        }
                    }
                }
            }
        },
    ) { padding ->
        val visible = state.visibleApps
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { SortChips(state.sort, onSort) }
            if (!state.hasUsageAccess) {
                item {
                    PermissionCard(
                        stringResource(R.string.perm_usage_title),
                        stringResource(R.string.perm_usage_desc),
                        onGrantUsage,
                        Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            when {
                state.loading -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                visible.isEmpty() -> item {
                    EmptyState(Icons.Rounded.Apps, stringResource(R.string.files_empty), "")
                }
                else -> {
                    item {
                        Text(
                            pluralStringResource(R.plurals.apps_count, visible.size, visible.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            sort = state.sort,
                            checked = app.packageName in state.selected,
                            now = state.now,
                            onToggle = { onToggle(app.packageName) },
                            onOpen = { onOpenApp(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChips(selected: AppSort, onSort: (AppSort) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSort.entries.forEach { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSort(sort) },
                label = { Text(stringResource(sort.label())) },
            )
        }
    }
}

private fun AppSort.label(): Int = when (this) {
    AppSort.SIZE -> R.string.apps_sort_size
    AppSort.CACHE -> R.string.apps_sort_cache
    AppSort.USAGE -> R.string.apps_sort_usage
    AppSort.UNUSED -> R.string.apps_sort_unused
    AppSort.RECENT -> R.string.apps_sort_recent
}

@Composable
private fun AppRow(app: AppEntry, sort: AppSort, checked: Boolean, now: Long, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, app.label, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isSystem) {
                    Spacer(Modifier.width(6.dp))
                    Tag(stringResource(R.string.apps_system_badge))
                }
                if (AppsLogic.isUnused(app, now)) {
                    Spacer(Modifier.width(6.dp))
                    Tag(
                        stringResource(R.string.apps_unused_badge),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        background = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
            }
            Text(
                lastUsedText(app, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            trailingValue(app, sort),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun lastUsedText(app: AppEntry, now: Long): String {
    val last = app.lastUsedAt ?: return stringResource(R.string.apps_never_used)
    return stringResource(
        R.string.apps_last_used,
        DateUtils.getRelativeTimeSpanString(last, now, DateUtils.MINUTE_IN_MILLIS).toString(),
    )
}

private fun trailingValue(app: AppEntry, sort: AppSort): String = when (sort) {
    AppSort.CACHE -> app.cacheBytes?.let(::formatBytes) ?: "—"
    AppSort.USAGE -> formatDuration(app.foregroundMs24h)
    else -> app.totalBytes?.let(::formatBytes) ?: "—"
}
