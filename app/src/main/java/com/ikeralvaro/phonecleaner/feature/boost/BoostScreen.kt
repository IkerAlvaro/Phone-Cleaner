package com.ikeralvaro.phonecleaner.feature.boost

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import com.ikeralvaro.phonecleaner.ui.components.AppIcon
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.EmptyState
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.components.SectionHeader
import com.ikeralvaro.phonecleaner.ui.components.UsageRing
import com.ikeralvaro.phonecleaner.ui.components.rememberSequentialLauncher
import kotlin.math.roundToInt

@Composable
fun BoostScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { BoostViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberPermissionActions()
    val manager = LocalAppContainer.current.permissionManager

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    val launchQuickStop = rememberSequentialLauncher(
        intentFor = manager::appDetailsIntent,
        onReturned = viewModel::onReturnedFromSettings,
    )

    BoostContent(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onGrantUsage = actions.requestUsage,
        onBoost = {
            if (state.canStopOtherApps) viewModel.stopInBackground() else viewModel.startQuickStop()?.let(launchQuickStop)
        },
    )
}

@Composable
fun BoostContent(
    state: BoostUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onGrantUsage: () -> Unit,
    onBoost: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(
        topBar = { PhoneCleanerTopBar(stringResource(R.string.boost_title), onBack = onBack) },
        bottomBar = {
            if (state.phase == BoostPhase.READY || state.phase == BoostPhase.DONE) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        BigActionButton(
                            text = if (state.canStopOtherApps) {
                                stringResource(R.string.boost_button)
                            } else {
                                stringResource(R.string.boost_quick_stop_button, state.selected.size)
                            },
                            onClick = onBoost,
                            enabled = state.selected.isNotEmpty(),
                            icon = Icons.Rounded.Memory,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MemoryHeader(state) }
            when (state.phase) {
                BoostPhase.WORKING -> item { WorkingRow(stringResource(R.string.boost_working)) }
                BoostPhase.QUICK_STOP -> item {
                    WorkingRow(
                        stringResource(
                            R.string.quick_stop_progress,
                            state.quickStopPosition,
                            state.quickStopTotal,
                            state.quickStopLabel.orEmpty(),
                        ),
                    )
                }
                BoostPhase.DONE -> item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (state.freedBytes > 0) {
                                    stringResource(R.string.boost_result_freed, formatBytes(state.freedBytes))
                                } else {
                                    stringResource(R.string.boost_result_none)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                else -> Unit
            }
            if (!state.canStopOtherApps) {
                item {
                    SectionCard {
                        Row {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.boost_quick_stop_explain), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (!state.hasUsageAccess) {
                item {
                    PermissionCard(stringResource(R.string.perm_usage_title), stringResource(R.string.boost_needs_usage), onGrantUsage)
                }
            }
            if (state.phase == BoostPhase.LOADING) {
                item { WorkingRow(null) }
            } else if (state.candidates.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.TaskAlt, stringResource(R.string.boost_no_candidates), "")
                }
            } else {
                item { SectionHeader(stringResource(R.string.boost_candidates)) }
                items(state.candidates, key = { it.packageName }) { app ->
                    CandidateRow(app, app.packageName in state.selected, now) { onToggle(app.packageName) }
                }
            }
        }
    }
}

@Composable
private fun MemoryHeader(state: BoostUiState) {
    val memory = state.memory
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        UsageRing(progress = memory?.usedFraction ?: 0f, size = 180.dp, strokeWidth = 16.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    memory?.let { "${(it.usedFraction * 100).roundToInt()} %" } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("RAM", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (memory != null) {
            Text(
                stringResource(R.string.boost_in_use, formatBytes(memory.usedBytes), formatBytes(memory.totalBytes)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun WorkingRow(text: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        if (text != null) {
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CandidateRow(app: AppEntry, checked: Boolean, now: Long, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, app.label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val last = app.lastUsedAt
            if (last != null) {
                Text(
                    stringResource(
                        R.string.apps_last_used,
                        DateUtils.getRelativeTimeSpanString(last, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
