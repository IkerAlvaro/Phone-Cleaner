package com.ikeralvaro.phonecleaner.core.permissions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ikeralvaro.phonecleaner.LocalAppContainer

/** Estado de los permisos, recalculado cada vez que la pantalla vuelve a primer plano. */
@Composable
fun rememberPermissionSnapshot(): State<PermissionSnapshot> {
    val manager = LocalAppContainer.current.permissionManager
    val state = remember { mutableStateOf(manager.snapshot()) }
    LifecycleResumeEffect(manager) {
        state.value = manager.snapshot()
        onPauseOrDispose { }
    }
    return state
}

@Stable
class PermissionActions(
    val requestAllFiles: () -> Unit,
    val requestMedia: () -> Unit,
    val requestNotifications: () -> Unit,
)

/** Acciones para pedir cada permiso: abre Ajustes o muestra el diálogo del sistema según el caso. */
@Composable
fun rememberPermissionActions(onChanged: () -> Unit = {}): PermissionActions {
    val context = LocalContext.current
    val manager = LocalAppContainer.current.permissionManager

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val activity = context.findActivity()
        val permanentlyDenied = results.any { (permission, granted) ->
            !granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        if (permanentlyDenied) context.startSafely(manager.appDetailsIntent())
        onChanged()
    }

    return remember(context, manager) {
        PermissionActions(
            requestAllFiles = {
                val intent = manager.allFilesAccessIntent()
                if (intent == null) {
                    runtimeLauncher.launch(manager.legacyStoragePermissions())
                } else if (!context.startSafely(intent)) {
                    manager.allFilesAccessFallbackIntent()?.let(context::startSafely)
                }
            },
            requestMedia = { runtimeLauncher.launch(manager.mediaPermissions()) },
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) {
                    runtimeLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    context.startSafely(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        )
    }
}

/** Abre una actividad sin cerrar la app si el móvil no la tiene. Devuelve false si no existe. */
fun Context.startSafely(intent: Intent): Boolean = try {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
