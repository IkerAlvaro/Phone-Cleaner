package com.ikeralvaro.phonecleaner.feature.settings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.core.permissions.PermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.PermissionSnapshot
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionSnapshot
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val repository = LocalAppContainer.current.settingsRepository
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val permissions by rememberPermissionSnapshot()
    val actions = rememberPermissionActions()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SettingsContent(
        settings = settings,
        permissions = permissions,
        actions = actions,
        version = version,
        onBack = onBack,
        onTheme = { scope.launch { repository.setThemeMode(it) } },
        onReminder = { scope.launch { repository.setReminderEnabled(it) } },
    )
}

@Composable
fun SettingsContent(
    settings: AppSettings,
    permissions: PermissionSnapshot,
    actions: PermissionActions,
    version: String,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onReminder: (Boolean) -> Unit,
) {
    Scaffold(topBar = { PhoneCleanerTopBar(stringResource(R.string.settings_title), onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_theme)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = settings.themeMode == mode, onClick = { onTheme(mode) }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.themeMode == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            SectionCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = settings.reminderEnabled, onValueChange = onReminder, role = Role.Switch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_reminder), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_reminder_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.reminderEnabled, onCheckedChange = null)
                }
            }

            SectionCard(title = stringResource(R.string.settings_permissions)) {
                PermissionRow(R.string.perm_files_title, R.string.perm_files_desc, permissions.allFiles, actions.requestAllFiles)
                PermissionRow(R.string.perm_usage_title, R.string.perm_usage_desc, permissions.usage, actions.requestUsage)
                PermissionRow(R.string.perm_media_title, R.string.perm_media_desc, permissions.media, actions.requestMedia)
                PermissionRow(
                    R.string.perm_notifications_title,
                    R.string.perm_notifications_desc,
                    permissions.notifications,
                    actions.requestNotifications,
                )
            }

            SectionCard(title = stringResource(R.string.settings_language)) {
                Text(stringResource(R.string.settings_language_desc), style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.app_name) + " · " + stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermissionRow(title: Int, description: Int, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.granted), tint = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onGrant) { Text(stringResource(R.string.action_grant)) }
        }
    }
}
