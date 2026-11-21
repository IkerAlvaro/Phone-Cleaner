package com.ikeralvaro.phonecleaner.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ikeralvaro.phonecleaner.BuildConfig
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.core.billing.PurchaseMessage
import com.ikeralvaro.phonecleaner.core.permissions.PermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.findActivity
import com.ikeralvaro.phonecleaner.core.permissions.PermissionSnapshot
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionSnapshot
import com.ikeralvaro.phonecleaner.core.permissions.startSafely
import com.ikeralvaro.phonecleaner.ui.components.PhoneCleanerTopBar
import com.ikeralvaro.phonecleaner.ui.components.SectionCard
import kotlinx.coroutines.launch

/** Estado de la compra y de los anuncios que se muestra en Ajustes. */
data class AdsSettingsState(
    val price: String? = null,
    val message: PurchaseMessage = PurchaseMessage.NONE,
    val privacyOptionsRequired: Boolean = false,
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val repository = container.settingsRepository
    val price by container.billingManager.price.collectAsStateWithLifecycle()
    val message by container.billingManager.message.collectAsStateWithLifecycle()
    val privacyRequired by container.consentManager.privacyOptionsRequired.collectAsStateWithLifecycle()
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
        ads = AdsSettingsState(price, message, privacyRequired),
        onRemoveAds = {
            container.billingManager.consumeMessage()
            context.findActivity()?.let(container.billingManager::launchPurchase)
        },
        onAdsPrivacy = { context.findActivity()?.let(container.consentManager::showPrivacyOptions) },
        onPrivacyPolicy = { context.startSafely(Intent(Intent.ACTION_VIEW, BuildConfig.PRIVACY_POLICY_URL.toUri())) },
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
    ads: AdsSettingsState = AdsSettingsState(),
    onRemoveAds: () -> Unit = {},
    onAdsPrivacy: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
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
            RemoveAdsCard(settings.premium, ads, onRemoveAds)

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

            SectionCard {
                if (ads.privacyOptionsRequired) {
                    LinkRow(stringResource(R.string.ads_privacy), stringResource(R.string.ads_privacy_desc), onAdsPrivacy)
                }
                LinkRow(stringResource(R.string.privacy_policy), null, onPrivacyPolicy)
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
private fun RemoveAdsCard(premium: Boolean, ads: AdsSettingsState, onRemoveAds: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (premium) Icons.Rounded.Favorite else Icons.Rounded.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (premium) R.string.remove_ads_thanks else R.string.remove_ads_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (!premium) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.remove_ads_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRemoveAds, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        ads.price?.let { stringResource(R.string.remove_ads_buy, it) }
                            ?: stringResource(R.string.remove_ads_buy_no_price),
                    )
                }
                when (ads.message) {
                    PurchaseMessage.UNAVAILABLE -> MessageText(stringResource(R.string.remove_ads_unavailable))
                    PurchaseMessage.ERROR -> MessageText(stringResource(R.string.remove_ads_error))
                    PurchaseMessage.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun MessageText(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun LinkRow(title: String, description: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
