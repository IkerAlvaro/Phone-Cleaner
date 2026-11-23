package com.ikeralvaro.phonecleaner.feature.apps

import com.ikeralvaro.phonecleaner.ui.icons.AppIcons
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import com.ikeralvaro.phonecleaner.ui.components.ConfirmDialog
import com.ikeralvaro.phonecleaner.ui.components.GradientHeader
import com.ikeralvaro.phonecleaner.ui.components.SecondaryButton
import com.ikeralvaro.phonecleaner.ui.components.bottomContentPadding
import com.ikeralvaro.phonecleaner.ui.components.listPadding
import com.ikeralvaro.phonecleaner.ui.theme.cardBorderColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.core.util.relativeTime
import com.ikeralvaro.phonecleaner.ui.components.AppIcon
import com.ikeralvaro.phonecleaner.ui.components.EmptyState
import com.ikeralvaro.phonecleaner.ui.components.Tag
import com.ikeralvaro.phonecleaner.ui.components.rememberSequentialLauncher

private enum class SequenceMode { STOP, UNINSTALL }

fun uninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))

@Composable
fun AppsScreen(onOpenApp: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val viewModel = appViewModel { AppsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    onStopSelected: () -> Unit,
    onUninstallSelected: () -> Unit,
    initialConfirm: Boolean = false,
) {
    val count = state.selected.size
    var confirmUninstall by rememberSaveable { mutableStateOf(initialConfirm) }
    if (confirmUninstall && count > 0) {
        ConfirmDialog(
            title = stringResource(R.string.apps_confirm_uninstall_title),
            message = pluralStringResource(R.plurals.apps_confirm_uninstall_message, count, count),
            confirmLabel = stringResource(R.string.apps_uninstall_selected, count),
            onConfirm = {
                confirmUninstall = false
                onUninstallSelected()
            },
            onDismiss = { confirmUninstall = false },
            icon = AppIcons.Delete,
            destructive = true,
        )
    }
    Scaffold(
        topBar = { GradientHeader(stringResource(R.string.apps_title), onBack = onBack) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (count > 0) {
                // Barra de selección por encima de la barra píldora (o de la barra del sistema).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, cardBorderColor()),
                    modifier = Modifier
                        .padding(bottom = bottomContentPadding())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SecondaryButton(
                            text = stringResource(R.string.apps_stop_selected, count),
                            onClick = onStopSelected,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = stringResource(R.string.apps_uninstall_selected, count),
                            onClick = { confirmUninstall = true },
                            modifier = Modifier.weight(1f),
                            destructive = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val visible = remember(state.apps, state.sort) { state.visibleApps }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = listPadding(padding, vertical = 8.dp),
        ) {
            item(key = "sort", contentType = "sort") { SortChips(state.sort, onSort) }
            when {
                state.loading -> item(key = "loading", contentType = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                visible.isEmpty() -> item(key = "empty", contentType = "empty") {
                    if (state.sort == AppSort.AUTOSTART) {
                        EmptyState(
                            AppIcons.TaskAlt,
                            stringResource(R.string.apps_autostart_empty),
                            stringResource(R.string.apps_autostart_empty_desc),
                        )
                    } else {
                        EmptyState(AppIcons.Apps, stringResource(R.string.files_empty), stringResource(R.string.apps_empty_desc))
                    }
                }
                else -> {
                    item(key = "count", contentType = "count") {
                        Text(
                            pluralStringResource(R.plurals.apps_count, visible.size, visible.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(visible, key = { it.packageName }, contentType = { "app" }) { app ->
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
    AppSort.NAME -> R.string.apps_sort_name
    AppSort.NEWEST -> R.string.apps_sort_newest
    AppSort.AUTOSTART -> R.string.apps_sort_autostart
}

@Composable
private fun AppRow(app: AppEntry, sort: AppSort, checked: Boolean, now: Long, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, app.label, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (sort == AppSort.NEWEST) {
                    stringResource(R.string.apps_installed, relativeTime(app.installedAt, now))
                } else {
                    stringResource(R.string.apps_updated, relativeTime(app.updatedAt, now))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.isSystem || app.startsAtBoot || app.ignoresBatteryOptimizations) {
                FlowRow(
                    Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (app.isSystem) Tag(stringResource(R.string.apps_system_badge))
                    if (app.startsAtBoot) {
                        Tag(
                            stringResource(R.string.apps_autostart_badge),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            background = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                    if (app.ignoresBatteryOptimizations) {
                        Tag(
                            stringResource(R.string.apps_unrestricted_badge),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatBytes(app.apkBytes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
