package com.ikeralvaro.phonecleaner.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.system.CoreFreq
import com.ikeralvaro.phonecleaner.core.system.ThermalLevel
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.core.util.formatDuration
import com.ikeralvaro.phonecleaner.ui.components.IconBadge
import com.ikeralvaro.phonecleaner.ui.components.KeyValueRow
import com.ikeralvaro.phonecleaner.ui.components.LinearMeter
import com.ikeralvaro.phonecleaner.ui.components.MiniStat
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MonitorScreen(onOpenBattery: () -> Unit, onOpenBoost: () -> Unit, onBatterySaver: () -> Unit) {
    val viewModel = appViewModel { MonitorViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MonitorContent(state, onOpenBattery, onOpenBoost, onBatterySaver)
}

@Composable
fun MonitorContent(
    state: MonitorUiState,
    onOpenBattery: () -> Unit,
    onOpenBoost: () -> Unit,
    onBatterySaver: () -> Unit,
) {
    Scaffold(topBar = { PhoneCleanerTopBar(stringResource(R.string.monitor_title)) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CpuCard(state)
            ThermalCard(state, onOpenBoost, onBatterySaver)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val memory = state.memory
                MiniStat(
                    icon = Icons.Rounded.Memory,
                    label = stringResource(R.string.monitor_ram),
                    value = memory?.let { "${(it.usedFraction * 100).roundToInt()} %" } ?: "—",
                    detail = memory?.let { stringResource(R.string.monitor_free, formatBytes(it.availableBytes)) },
                    progress = memory?.usedFraction,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenBoost,
                )
                val storage = state.storage
                MiniStat(
                    icon = Icons.Rounded.Storage,
                    label = stringResource(R.string.monitor_storage),
                    value = storage?.let { "${(it.usedFraction * 100).roundToInt()} %" } ?: "—",
                    detail = storage?.let { stringResource(R.string.monitor_free, formatBytes(it.freeBytes)) },
                    progress = storage?.usedFraction,
                    modifier = Modifier.weight(1f),
                )
            }
            val battery = state.battery
            MiniStat(
                icon = Icons.Rounded.BatteryChargingFull,
                label = stringResource(R.string.battery_title),
                value = battery?.let { "${it.levelPercent} %" } ?: "—",
                detail = battery?.temperatureC?.let { String.format(Locale.getDefault(), "%.1f °C", it) },
                progress = battery?.levelPercent?.div(100f),
                onClick = onOpenBattery,
            )
            state.device?.let { device ->
                SectionCard(title = stringResource(R.string.monitor_device)) {
                    KeyValueRow(stringResource(R.string.monitor_model), device.model)
                    KeyValueRow(stringResource(R.string.monitor_android), device.androidVersion)
                    KeyValueRow(stringResource(R.string.monitor_uptime), formatDuration(device.uptimeMs))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CpuCard(state: MonitorUiState) {
    val cpu = state.cpu
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.DeveloperBoard)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.monitor_cpu), style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(cpu?.socName?.takeIf { it.isNotBlank() }, cpu?.let { stringResource(R.string.monitor_cores, it.cores) })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cpu?.estimatedLoad?.let {
                Text("${(it * 100).roundToInt()} %", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        val load = cpu?.estimatedLoad
        if (load != null) {
            Text(stringResource(R.string.monitor_cpu_activity), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            LinearMeter(load)
            Spacer(Modifier.height(12.dp))
            cpu.freqs.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { core -> CoreRow(core, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                stringResource(R.string.monitor_cpu_activity_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (cpu != null) {
            Text(
                stringResource(R.string.monitor_freq_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoreRow(core: CoreFreq, modifier: Modifier) {
    Column(modifier) {
        Row {
            Text(
                stringResource(R.string.monitor_core, core.index),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(formatFrequency(core.curKhz), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        val max = core.maxKhz
        val cur = core.curKhz
        LinearMeter(if (max != null && cur != null && max > 0) cur.toFloat() / max else 0f, height = 6.dp)
    }
}

fun formatFrequency(khz: Long?): String = when {
    khz == null -> "—"
    khz >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f GHz", khz / 1_000_000.0)
    else -> "${khz / 1000} MHz"
}

@Composable
private fun ThermalCard(state: MonitorUiState, onOpenBoost: () -> Unit, onBatterySaver: () -> Unit) {
    val thermal = state.thermal
    val cpuTemp = thermal?.cpuTempC
    val batteryTemp = state.battery?.temperatureC
    val level = thermal?.level ?: ThermalLevel.UNKNOWN
    val hot = level >= ThermalLevel.MODERATE && level != ThermalLevel.UNKNOWN ||
        (cpuTemp ?: batteryTemp ?: 0f) >= 42f
    val color: Color = when {
        level >= ThermalLevel.SEVERE && level != ThermalLevel.UNKNOWN -> MaterialTheme.colorScheme.error
        hot -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Thermostat, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.monitor_temperature), style = MaterialTheme.typography.titleMedium)
                if (level != ThermalLevel.UNKNOWN) {
                    Text(
                        stringResource(R.string.monitor_thermal) + ": " + stringResource(level.label()),
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                    )
                }
            }
            val tempText = when {
                cpuTemp != null -> String.format(Locale.getDefault(), "%.1f °C", cpuTemp)
                batteryTemp != null -> stringResource(
                    R.string.monitor_temp_battery,
                    String.format(Locale.getDefault(), "%.1f °C", batteryTemp),
                )
                else -> "—"
            }
            Text(tempText, style = MaterialTheme.typography.titleLarge, color = color)
        }
        if (hot) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.monitor_hot_advice), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBoost) { Text(stringResource(R.string.tool_boost)) }
                OutlinedButton(onClick = onBatterySaver) { Text(stringResource(R.string.tip_saver)) }
            }
        }
    }
}

private fun ThermalLevel.label(): Int = when (this) {
    ThermalLevel.NONE -> R.string.thermal_none
    ThermalLevel.LIGHT -> R.string.thermal_light
    ThermalLevel.MODERATE -> R.string.thermal_moderate
    ThermalLevel.SEVERE -> R.string.thermal_severe
    ThermalLevel.CRITICAL -> R.string.thermal_critical
    ThermalLevel.UNKNOWN -> R.string.unknown
}
