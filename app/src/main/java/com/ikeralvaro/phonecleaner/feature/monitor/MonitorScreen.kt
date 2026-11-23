package com.ikeralvaro.phonecleaner.feature.monitor

import com.ikeralvaro.phonecleaner.ui.icons.AppIcons
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.components.currentLocale
import com.ikeralvaro.phonecleaner.ui.components.formatCelsius
import com.ikeralvaro.phonecleaner.ui.components.formatNumber
import com.ikeralvaro.phonecleaner.ui.components.percent
import com.ikeralvaro.phonecleaner.ui.components.percentOf
import com.ikeralvaro.phonecleaner.ui.components.GradientHeader
import com.ikeralvaro.phonecleaner.ui.components.SecondaryButton
import com.ikeralvaro.phonecleaner.ui.components.listPadding
import com.ikeralvaro.phonecleaner.core.system.CpuSnapshot
import com.ikeralvaro.phonecleaner.core.system.ThermalSnapshot
import androidx.compose.foundation.layout.WindowInsets
import java.util.Locale

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
    Scaffold(
        topBar = { GradientHeader(stringResource(R.string.monitor_title)) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(listPadding(padding)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cada tarjeta recibe solo los datos que pinta: así no se recomponen todas en cada tic.
            CpuCard(state.cpu)
            ThermalCard(state.thermal, state.battery?.temperatureC, onOpenBoost, onBatterySaver)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val memory = state.memory
                MiniStat(
                    icon = AppIcons.Memory,
                    label = stringResource(R.string.monitor_ram),
                    value = memory?.let { percent(it.usedFraction) } ?: "—",
                    detail = memory?.let { stringResource(R.string.monitor_free, formatBytes(it.availableBytes)) },
                    progress = memory?.usedFraction,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenBoost,
                )
                val storage = state.storage
                MiniStat(
                    icon = AppIcons.Storage,
                    label = stringResource(R.string.monitor_storage),
                    value = storage?.let { percent(it.usedFraction) } ?: "—",
                    detail = storage?.let { stringResource(R.string.monitor_free, formatBytes(it.freeBytes)) },
                    progress = storage?.usedFraction,
                    modifier = Modifier.weight(1f),
                )
            }
            val battery = state.battery
            MiniStat(
                icon = AppIcons.BatteryChargingFull,
                label = stringResource(R.string.battery_title),
                value = battery?.let { percentOf(it.levelPercent) } ?: "—",
                detail = battery?.temperatureC?.let { formatCelsius(it, currentLocale()) },
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
        }
    }
}

@Composable
private fun CpuCard(cpu: CpuSnapshot?) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.DeveloperBoard)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.monitor_cpu), style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(cpu?.socName?.takeIf { it.isNotBlank() }, cpu?.let { pluralStringResource(R.plurals.cores_count, it.cores, it.cores) })
                        .joinToString(stringResource(R.string.separator_dot)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cpu?.estimatedLoad?.let {
                Text(percent(it), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
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
            Text(formatFrequency(core.curKhz, currentLocale()), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        val max = core.maxKhz
        val cur = core.curKhz
        LinearMeter(if (max != null && cur != null && max > 0) cur.toFloat() / max else 0f, height = 6.dp)
    }
}

fun formatFrequency(khz: Long?, locale: Locale): String = when {
    khz == null -> "—"
    khz >= 1_000_000 -> String.format(locale, "%.2f GHz", khz / 1_000_000.0)
    else -> formatNumber(khz / 1000, locale) + " MHz"
}

@Composable
private fun ThermalCard(thermal: ThermalSnapshot?, batteryTemp: Float?, onOpenBoost: () -> Unit, onBatterySaver: () -> Unit) {
    val cpuTemp = thermal?.cpuTempC
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
            IconBadge(AppIcons.Thermostat, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.monitor_temperature), style = MaterialTheme.typography.titleMedium)
                if (level != ThermalLevel.UNKNOWN) {
                    Text(
                        stringResource(R.string.label_value, stringResource(R.string.monitor_thermal), stringResource(level.label())),
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                    )
                }
            }
            val locale = currentLocale()
            val tempText = when {
                cpuTemp != null -> formatCelsius(cpuTemp, locale)
                batteryTemp != null -> stringResource(R.string.monitor_temp_battery, formatCelsius(batteryTemp, locale))
                else -> "—"
            }
            Text(tempText, style = MaterialTheme.typography.titleLarge, color = color)
        }
        if (hot) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.monitor_hot_advice), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.tool_boost), onOpenBoost, Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.tip_saver), onBatterySaver, Modifier.weight(1f))
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
