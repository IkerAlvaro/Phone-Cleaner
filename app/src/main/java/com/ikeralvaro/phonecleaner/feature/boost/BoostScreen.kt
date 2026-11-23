package com.ikeralvaro.phonecleaner.feature.boost

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ikeralvaro.phonecleaner.core.permissions.findActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
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
import com.ikeralvaro.phonecleaner.feature.cleaner.CancelDialog
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.BottomAction
import com.ikeralvaro.phonecleaner.ui.components.GradientHeader
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.components.UsageRing
import com.ikeralvaro.phonecleaner.ui.components.bottomContentPadding
import com.ikeralvaro.phonecleaner.ui.components.percent
import com.ikeralvaro.phonecleaner.ui.icons.AppIcons

@Composable
fun BoostScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { BoostViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    // Si la app pasa a segundo plano a mitad, se cancela y se suelta la memoria.
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelIfNotChangingConfigurations(context.findActivity()) }
    // El anuncio de pantalla completa solo sale con el botón explícito "Hecho", nunca con "atrás".
    val finishWithAd = rememberFinishWithAd()
    val running = state.phase == BoostPhase.RUNNING
    var askCancel by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = running) { askCancel = true }
    if (askCancel && running) {
        CancelDialog(
            message = stringResource(R.string.boost_cancel_message),
            onConfirm = {
                askCancel = false
                viewModel.cancel()
                onBack()
            },
            onDismiss = { askCancel = false },
        )
    }
    BoostContent(
        state,
        onBack = { if (running) askCancel = true else onBack() },
        onBoost = viewModel::start,
        onFinish = { finishWithAd(onBack) },
    )
}

@Composable
fun BoostContent(state: BoostUiState, onBack: () -> Unit, onBoost: () -> Unit, onFinish: () -> Unit = onBack) {
    val running = state.phase == BoostPhase.RUNNING
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            BottomAction {
                if (state.phase == BoostPhase.DONE) {
                    BigActionButton(text = stringResource(R.string.action_done), onClick = onFinish, icon = AppIcons.TaskAlt)
                } else {
                    BigActionButton(
                        text = stringResource(R.string.boost_button),
                        onClick = onBoost,
                        enabled = !running,
                        icon = AppIcons.Memory,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomContentPadding(padding.calculateBottomPadding())),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GradientHeader(stringResource(R.string.boost_title), onBack = onBack) {
                UsageRing(
                    progress = state.usedFraction,
                    size = 180.dp,
                    strokeWidth = 16.dp,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            percent(state.usedFraction),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                        )
                        Text(
                            stringResource(R.string.ram_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                state.memory?.let { memory ->
                    Text(
                        stringResource(R.string.boost_in_use, formatBytes(memory.usedBytes), formatBytes(memory.totalBytes)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                            Icon(AppIcons.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        Icon(AppIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
            }
        }
    }
}

private fun BoostStage?.label(): Int = when (this) {
    BoostStage.HOLDING -> R.string.boost_stage_holding
    BoostStage.RELEASING, BoostStage.MEASURING -> R.string.boost_stage_releasing
    BoostStage.FILLING, null -> R.string.boost_stage_filling
}
