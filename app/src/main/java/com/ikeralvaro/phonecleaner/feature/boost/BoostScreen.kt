package com.ikeralvaro.phonecleaner.feature.boost

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.ads.AdPlacement
import com.ikeralvaro.phonecleaner.core.ads.NativeAdCard
import com.ikeralvaro.phonecleaner.core.ads.rememberFinishWithAd
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.components.UsageRing
import kotlin.math.roundToInt

@Composable
fun BoostScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { BoostViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    // Si la app pasa a segundo plano a mitad, se cancela y se suelta la memoria.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancel() }
    val finishWithAd = rememberFinishWithAd()
    val leave = { if (state.phase == BoostPhase.DONE) finishWithAd(onBack) else onBack() }
    BackHandler(enabled = state.phase == BoostPhase.DONE) { leave() }
    BoostContent(state, onBack = leave, onBoost = viewModel::start)
}

@Composable
fun BoostContent(state: BoostUiState, onBack: () -> Unit, onBoost: () -> Unit) {
    val running = state.phase == BoostPhase.RUNNING
    Scaffold(
        topBar = { PhoneCleanerTopBar(stringResource(R.string.boost_title), onBack = if (running) null else onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(
                    Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    BigActionButton(
                        text = stringResource(R.string.boost_button),
                        onClick = onBoost,
                        enabled = !running,
                        icon = Icons.Rounded.Memory,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            UsageRing(
                progress = state.usedFraction,
                size = 220.dp,
                strokeWidth = 18.dp,
                color = if (running && state.usedFraction > 0.85f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(state.usedFraction * 100).roundToInt()} %",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("RAM", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.memory?.let { memory ->
                Text(
                    stringResource(R.string.boost_in_use, formatBytes(memory.usedBytes), formatBytes(memory.totalBytes)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            when (state.phase) {
                BoostPhase.RUNNING -> SectionCard {
                    Text(
                        stringResource(state.stage.label()),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                BoostPhase.DONE -> SectionCard {
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
                BoostPhase.IDLE -> Unit
            }
            if (state.phase == BoostPhase.DONE) {
                NativeAdCard(AdPlacement.RESULT)
            }

            SectionCard {
                Row {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.boost_explain), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.boost_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun BoostStage?.label(): Int = when (this) {
    BoostStage.HOLDING -> R.string.boost_stage_holding
    BoostStage.RELEASING, BoostStage.MEASURING -> R.string.boost_stage_releasing
    BoostStage.FILLING, null -> R.string.boost_stage_filling
}
