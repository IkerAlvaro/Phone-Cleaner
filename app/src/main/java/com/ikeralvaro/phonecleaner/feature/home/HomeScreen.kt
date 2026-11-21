package com.ikeralvaro.phonecleaner.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.ui.components.IconBadge
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import com.ikeralvaro.phonecleaner.ui.theme.brandGradient
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
import androidx.compose.material3.Surface
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
    // Surface: da el color de texto correcto (onBackground) en claro y en oscuro.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HomeHeader(state, onSettings)
        Column(Modifier.padding(horizontal = 16.dp)) {
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
            Spacer(Modifier.height(16.dp))
            BigActionButton(
                text = stringResource(R.string.home_quick_scan),
                onClick = onQuickScan,
                icon = Icons.Rounded.CleaningServices,
            )
            Spacer(Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.home_tools))
            ToolGrid(onTool)
            Spacer(Modifier.height(16.dp))
            TipCard(now)
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
}

/** Cabecera con el degradado del logo: logo, nombre y medidor de almacenamiento en blanco. */
@Composable
private fun HomeHeader(state: HomeUiState, onSettings: () -> Unit) {
    val storage = state.storage
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(brandGradient())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings), tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        UsageRing(
            progress = storage?.usedFraction ?: 0f,
            size = 200.dp,
            strokeWidth = 16.dp,
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.22f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = storage?.let { "${(it.usedFraction * 100).roundToInt()} %" } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.home_storage_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        if (storage != null) {
            Text(
                stringResource(R.string.home_storage_used, formatBytes(storage.usedBytes), formatBytes(storage.totalBytes)),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(3).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

private val DAILY_TIPS = listOf(
    R.string.daily_tip_1, R.string.daily_tip_2, R.string.daily_tip_3, R.string.daily_tip_4, R.string.daily_tip_5,
)

@Composable
private fun TipCard(now: Long) {
    val tip = DAILY_TIPS[((now / DAY_MS) % DAILY_TIPS.size).toInt()]
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Lightbulb)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.tip_of_day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium)
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
