package com.ikeralvaro.phonecleaner.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.core.ads.AdPlacement
import com.ikeralvaro.phonecleaner.core.ads.NativeAdCard
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.core.util.relativeTime
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.FeatureTile
import com.ikeralvaro.phonecleaner.ui.components.MiniStat
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionHeader
import com.ikeralvaro.phonecleaner.ui.components.UsageRing
import com.ikeralvaro.phonecleaner.ui.components.currentLocale
import com.ikeralvaro.phonecleaner.ui.components.formatCelsius
import kotlin.math.roundToInt

enum class HomeTool { CLEANER, BOOST, BATTERY, COMPRESS, APPS, FILES }

@Composable
fun HomeScreen(
    onQuickScan: () -> Unit,
    onTool: (HomeTool) -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HomeContent(state, onQuickScan, onTool, onSettings)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onQuickScan: () -> Unit,
    onTool: (HomeTool) -> Unit,
    onSettings: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(
        topBar = {
            PhoneCleanerTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            StorageHero(state)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val memory = state.memory
                MiniStat(
                    icon = Icons.Rounded.Memory,
                    label = stringResource(R.string.home_ram),
                    value = memory?.let { "${(it.usedFraction * 100).roundToInt()} %" } ?: "—",
                    detail = memory?.let { stringResource(R.string.home_storage_used, formatBytes(it.usedBytes), formatBytes(it.totalBytes)) },
                    progress = memory?.usedFraction,
                    modifier = Modifier.weight(1f),
                    onClick = { onTool(HomeTool.BOOST) },
                )
                val battery = state.battery
                MiniStat(
                    icon = Icons.Rounded.BatteryChargingFull,
                    label = stringResource(R.string.home_battery),
                    value = battery?.let { "${it.levelPercent} %" } ?: "—",
                    detail = battery?.temperatureC?.let { formatCelsius(it, currentLocale()) },
                    progress = battery?.levelPercent?.div(100f),
                    modifier = Modifier.weight(1f),
                    onClick = { onTool(HomeTool.BATTERY) },
                )
            }
            Spacer(Modifier.height(20.dp))
            BigActionButton(
                text = stringResource(R.string.home_quick_scan),
                onClick = onQuickScan,
                icon = Icons.Rounded.CleaningServices,
            )
            Spacer(Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.home_tools))
            ToolGrid(onTool)
            Spacer(Modifier.height(16.dp))
            NativeAdCard(AdPlacement.HOME)
            Spacer(Modifier.height(16.dp))
            Text(
                text = lastCleanText(state, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StorageHero(state: HomeUiState) {
    val storage = state.storage
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        UsageRing(progress = storage?.usedFraction ?: 0f, size = 220.dp, strokeWidth = 18.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = storage?.let { "${(it.usedFraction * 100).roundToInt()} %" } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.home_storage_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (storage != null) {
            Text(
                stringResource(R.string.home_storage_used, formatBytes(storage.usedBytes), formatBytes(storage.totalBytes)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ToolGrid(onTool: (HomeTool) -> Unit) {
    val tools = listOf(
        Triple(HomeTool.CLEANER, Icons.Rounded.CleaningServices, R.string.tool_cleaner to R.string.tool_cleaner_desc),
        Triple(HomeTool.BOOST, Icons.Rounded.Memory, R.string.tool_boost to R.string.tool_boost_desc),
        Triple(HomeTool.BATTERY, Icons.Rounded.BatteryChargingFull, R.string.tool_battery to R.string.tool_battery_desc),
        Triple(HomeTool.COMPRESS, Icons.Rounded.Compress, R.string.tool_compress to R.string.tool_compress_desc),
        Triple(HomeTool.APPS, Icons.Rounded.Apps, R.string.tool_apps to R.string.tool_apps_desc),
        Triple(HomeTool.FILES, Icons.Rounded.PhotoLibrary, R.string.tool_files to R.string.tool_files_desc),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (tool, icon, texts) ->
                    FeatureTile(
                        icon = icon,
                        title = stringResource(texts.first),
                        subtitle = stringResource(texts.second),
                        onClick = { onTool(tool) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun lastCleanText(state: HomeUiState, now: Long): String {
    if (state.lastCleanAt <= 0L) return stringResource(R.string.home_never_cleaned)
    val relative = relativeTime(state.lastCleanAt, now)
    return stringResource(R.string.home_last_clean, relative) + " · " +
        stringResource(R.string.home_total_freed, formatBytes(state.totalFreedBytes))
}
