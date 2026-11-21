package com.ikeralvaro.phonecleaner.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.core.permissions.PermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.PermissionSnapshot
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionActions
import com.ikeralvaro.phonecleaner.core.permissions.rememberPermissionSnapshot
import com.ikeralvaro.phonecleaner.ui.components.BigActionButton
import com.ikeralvaro.phonecleaner.ui.components.PermissionCard
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val repository = LocalAppContainer.current.settingsRepository
    val permissions by rememberPermissionSnapshot()
    val actions = rememberPermissionActions()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    OnboardingContent(
        step = step,
        permissions = permissions,
        actions = actions,
        onNext = { step = 1 },
        onFinish = {
            scope.launch {
                repository.setOnboardingDone()
                onFinished()
            }
        },
    )
}

@Composable
fun OnboardingContent(
    step: Int,
    permissions: PermissionSnapshot,
    actions: PermissionActions,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step == 0) {
                    Spacer(Modifier.height(48.dp))
                    Logo()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.onboarding_permissions_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    PermissionCard(
                        stringResource(R.string.perm_files_title),
                        stringResource(R.string.perm_files_desc),
                        actions.requestAllFiles,
                        granted = permissions.allFiles,
                    )
                    PermissionCard(
                        stringResource(R.string.perm_media_title),
                        stringResource(R.string.perm_media_desc),
                        actions.requestMedia,
                        granted = permissions.media,
                    )
                    PermissionCard(
                        stringResource(R.string.perm_notifications_title),
                        stringResource(R.string.perm_notifications_desc),
                        actions.requestNotifications,
                        granted = permissions.notifications,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (step == 0) {
                BigActionButton(stringResource(R.string.onboarding_next), onNext)
            } else {
                BigActionButton(stringResource(R.string.onboarding_start), onFinish)
            }
        }
    }
}

@Composable
private fun Logo() {
    Box(
        Modifier
            .size(132.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(132.dp))
    }
}
