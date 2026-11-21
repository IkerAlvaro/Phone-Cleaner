package com.ikeralvaro.phonecleaner.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.core.permissions.startSafely
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.core.util.relativeTime
import com.ikeralvaro.phonecleaner.ui.components.AppIcon
import com.ikeralvaro.phonecleaner.ui.components.KeyValueRow
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard

@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var app by remember { mutableStateOf<AppEntry?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    // Se recarga al volver de Ajustes o del diálogo de desinstalación.
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }
    LaunchedEffect(packageName, refreshKey) {
        app = container.appRepository.loadApp(packageName)
        loaded = true
        if (app == null && refreshKey > 1) onBack() // desinstalada
    }

    AppDetailContent(
        app = app,
        loading = !loaded,
        onBack = onBack,
        onOpenSettings = { context.startSafely(container.permissionManager.appDetailsIntent(packageName)) },
        onUninstall = { context.startSafely(uninstallIntent(packageName)) },
    )
}

@Composable
fun AppDetailContent(
    app: AppEntry?,
    loading: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(topBar = { PhoneCleanerTopBar(app?.label.orEmpty(), onBack = onBack) }) { padding ->
        if (app == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { if (loading) CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(app.packageName, app.label, size = 72.dp)
                Spacer(Modifier.height(8.dp))
                Text(app.label, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    listOfNotNull(app.versionName?.let { stringResource(R.string.app_detail_version, it) }, app.packageName)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            SectionCard(title = stringResource(R.string.app_detail_size)) {
                KeyValueRow(stringResource(R.string.app_detail_apk_size), formatBytes(app.apkBytes))
                Text(
                    stringResource(R.string.app_detail_size_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyValueRow(stringResource(R.string.app_detail_installed), relativeTime(app.installedAt, now).replaceFirstChar { it.uppercase() })
                KeyValueRow(stringResource(R.string.app_detail_updated), relativeTime(app.updatedAt, now).replaceFirstChar { it.uppercase() })
            }

            SectionCard(title = stringResource(R.string.app_detail_background)) {
                KeyValueRow(
                    stringResource(R.string.app_detail_autostart),
                    stringResource(if (app.startsAtBoot) R.string.yes else R.string.no),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        if (app.ignoresBatteryOptimizations) R.string.app_detail_battery_unrestricted else R.string.app_detail_battery_optimized,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (app.ignoresBatteryOptimizations) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                stringResource(R.string.app_detail_stop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.StopCircle, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.app_detail_stop))
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.app_detail_open_settings))
            }
            OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.app_detail_uninstall))
            }
        }
    }
}
