package com.ikeralvaro.phonecleaner.feature.battery

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.startSafely
import com.ikeralvaro.phonecleaner.core.system.BatteryHealth
import com.ikeralvaro.phonecleaner.core.system.BatterySnapshot
import com.ikeralvaro.phonecleaner.core.system.ChargeStatus
import com.ikeralvaro.phonecleaner.core.system.PowerSource
import com.ikeralvaro.phonecleaner.core.util.formatDuration
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import com.ikeralvaro.phonecleaner.ui.components.AppIcon
import com.ikeralvaro.phonecleaner.ui.components.CategoryRow
import com.ikeralvaro.phonecleaner.ui.components.KeyValueRow
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.components.UsageRing
import com.ikeralvaro.phonecleaner.ui.components.currentLocale
import com.ikeralvaro.phonecleaner.ui.components.formatCelsius

@Composable
fun BatteryScreen(onBack: () -> Unit, onOpenBoost: () -> Unit, onOpenApp: (String) -> Unit) {
    val viewModel = appViewModel { BatteryViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberPermissionActions()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.loadApps()
        onPauseOrDispose { }
    }
    BatteryContent(
        state = state,
        onBack = onBack,
        onGrantUsage = actions.requestUsage,
        onOpenApp = onOpenApp,
        onBatterySaver = { context.openBatterySaver() },
        onDisplay = { context.startSafely(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
        onOpenBoost = onOpenBoost,
        onUnrestrictedSettings = { context.startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
    )
}

/** Abre el ahorro de batería; si el móvil no tiene esa pantalla, abre los ajustes de batería generales. */
fun android.content.Context.openBatterySaver() {
    if (!startSafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))) {
        startSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
    }
}

@Composable
fun BatteryContent(
    state: BatteryUiState,
    onBack: () -> Unit,
    onGrantUsage: () -> Unit,
    onOpenApp: (String) -> Unit,
    onBatterySaver: () -> Unit,
    onDisplay: () -> Unit,
    onOpenBoost: () -> Unit,
    onUnrestrictedSettings: () -> Unit,
) {
    Scaffold(topBar = { PhoneCleanerTopBar(stringResource(R.string.battery_title), onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BatteryHeader(state.battery)
            state.battery?.let { DetailsCard(it) }

            SectionCard(title = stringResource(R.string.battery_top_apps)) {
                if (!state.hasUsageAccess) {
                    PermissionCard(stringResource(R.string.perm_usage_title), stringResource(R.string.perm_usage_desc), onGrantUsage)
                } else {
                    state.topApps.forEach { app -> AppUsageRow(app, formatDuration(app.foregroundMs24h)) { onOpenApp(app.packageName) } }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.battery_top_apps_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.battery_unrestricted)) {
                if (state.unrestrictedApps.isEmpty() && !state.loadingApps) {
                    Text(stringResource(R.string.battery_unrestricted_none), style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.unrestrictedApps.forEach { app ->
                        AppUsageRow(app, null) { onOpenApp(app.packageName) }
                    }
                    TextButton(onClick = onUnrestrictedSettings) { Text(stringResource(R.string.action_open_settings)) }
                }
            }

            SectionCard(title = stringResource(R.string.battery_tips)) {
                CategoryRow(stringResource(R.string.tip_saver), stringResource(R.string.tip_saver_desc), "", icon = Icons.Rounded.BatterySaver, onClick = onBatterySaver)
                CategoryRow(stringResource(R.string.tip_display), stringResource(R.string.tip_display_desc), "", icon = Icons.Rounded.Brightness6, onClick = onDisplay)
                CategoryRow(stringResource(R.string.tip_dark), stringResource(R.string.tip_dark_desc), "", icon = Icons.Rounded.DarkMode, onClick = onDisplay)
                CategoryRow(stringResource(R.string.tip_boost), stringResource(R.string.tip_boost_desc), "", icon = Icons.Rounded.Memory, onClick = onOpenBoost)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BatteryHeader(battery: BatterySnapshot?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val warning = battery != null && battery.levelPercent <= 15
        UsageRing(
            progress = (battery?.levelPercent ?: 0) / 100f,
            size = 180.dp,
            strokeWidth = 16.dp,
            color = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    battery?.let { "${it.levelPercent} %" } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
                battery?.let {
                    Text(stringResource(it.status.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(battery: BatterySnapshot) {
    val locale = currentLocale()
    SectionCard {
        battery.temperatureC?.let { KeyValueRow(stringResource(R.string.battery_temperature), formatCelsius(it, locale)) }
        KeyValueRow(stringResource(R.string.battery_health), stringResource(battery.health.label()))
        battery.voltageMv?.let { KeyValueRow(stringResource(R.string.battery_voltage), String.format(locale, "%.2f V", it / 1000.0)) }
        battery.currentMa?.let { KeyValueRow(stringResource(R.string.battery_current), "$it mA") }
        battery.estimatedCapacityMah?.let { KeyValueRow(stringResource(R.string.battery_capacity), "$it mAh") }
        battery.cycleCount?.let { KeyValueRow(stringResource(R.string.battery_cycles), it.toString()) }
        battery.technology?.let { KeyValueRow(stringResource(R.string.battery_technology), it) }
        KeyValueRow(stringResource(R.string.battery_source), stringResource(battery.source.label()))
    }
}

@Composable
private fun AppUsageRow(app: AppEntry, value: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, app.label, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onClick) { Text("›") }
    }
}

private fun ChargeStatus.label(): Int = when (this) {
    ChargeStatus.CHARGING -> R.string.battery_charging
    ChargeStatus.DISCHARGING -> R.string.battery_discharging
    ChargeStatus.FULL -> R.string.battery_full
    ChargeStatus.NOT_CHARGING, ChargeStatus.UNKNOWN -> R.string.battery_not_charging
}

private fun BatteryHealth.label(): Int = when (this) {
    BatteryHealth.GOOD -> R.string.health_good
    BatteryHealth.OVERHEAT -> R.string.health_overheat
    BatteryHealth.DEAD -> R.string.health_dead
    BatteryHealth.OVER_VOLTAGE -> R.string.health_over_voltage
    BatteryHealth.COLD -> R.string.health_cold
    BatteryHealth.FAILURE -> R.string.health_failure
    BatteryHealth.UNKNOWN -> R.string.unknown
}

private fun PowerSource.label(): Int = when (this) {
    PowerSource.AC, PowerSource.DOCK -> R.string.battery_source_ac
    PowerSource.USB -> R.string.battery_source_usb
    PowerSource.WIRELESS -> R.string.battery_source_wireless
    PowerSource.NONE -> R.string.battery_source_none
}
