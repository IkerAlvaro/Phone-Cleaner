package com.ikeralvaro.phonecleaner.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val allFiles: Boolean,
    val media: Boolean,
    val notifications: Boolean,
) {
    companion object {
        val ALL_GRANTED = PermissionSnapshot(allFiles = true, media = true, notifications = true)
    }
}

/** Comprueba y solicita los permisos que usa la app. */
class PermissionManager(private val context: Context) {

    fun snapshot() = PermissionSnapshot(
        allFiles = hasAllFilesAccess(),
        media = hasMediaAccess(),
        notifications = hasNotifications(),
    )

    fun hasAllFilesAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        else -> granted(Manifest.permission.WRITE_EXTERNAL_STORAGE) && granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasMediaAccess(): Boolean = hasAllFilesAccess() || mediaReadPermissions().all(::granted)

    fun hasNotifications(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        granted(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Permisos en tiempo de ejecución equivalentes a "todos los archivos" en Android 8–10. */
    fun legacyStoragePermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    private fun mediaReadPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Permisos a pedir para fotos y vídeos; en Android 10+ se añade la ubicación de las fotos (para conservar el GPS). */
    fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 29) mediaReadPermissions() + Manifest.permission.ACCESS_MEDIA_LOCATION else mediaReadPermissions()

    /** Pantalla de Ajustes para conceder "acceso a todos los archivos" (Android 11+). */
    fun allFilesAccessIntent(): Intent? {
        if (Build.VERSION.SDK_INT < 30) return null
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri())
    }

    fun allFilesAccessFallbackIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) else null

    fun appDetailsIntent(packageName: String = context.packageName): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
