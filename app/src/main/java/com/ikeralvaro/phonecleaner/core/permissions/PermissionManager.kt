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
    /** Android 14+: el usuario solo ha dado acceso a algunas fotos y vídeos (acceso parcial). */
    val mediaLimited: Boolean = false,
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
        mediaLimited = hasLimitedMediaAccess(),
    )

    fun hasAllFilesAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        else -> granted(Manifest.permission.WRITE_EXTERNAL_STORAGE) && granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * Acceso completo a fotos y vídeos. En Android 10 o inferior además hace falta poder escribir,
     * porque las funciones de fotos borran y guardan archivos.
     */
    fun hasMediaAccess(): Boolean = hasAllFilesAccess() || mediaReadPermissions().all(::granted)

    /** Android 14+: solo hay acceso a una selección de fotos y vídeos. */
    fun hasLimitedMediaAccess(): Boolean =
        Build.VERSION.SDK_INT >= 34 && !hasMediaAccess() && granted(READ_MEDIA_VISUAL_USER_SELECTED)

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
        Build.VERSION.SDK_INT >= 30 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        // Android 10 o inferior: borrar y guardar fotos necesita también permiso de escritura.
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    /** Permisos a pedir para fotos y vídeos; en Android 10+ se añade la ubicación de las fotos (para conservar el GPS). */
    /** En Android 14+ se incluye el acceso parcial para que el sistema ofrezca "seleccionar fotos". */
    fun mediaPermissions(): Array<String> {
        var permissions = mediaReadPermissions()
        if (Build.VERSION.SDK_INT >= 34) permissions += READ_MEDIA_VISUAL_USER_SELECTED
        if (Build.VERSION.SDK_INT >= 29) permissions += Manifest.permission.ACCESS_MEDIA_LOCATION
        return permissions
    }

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

    private companion object {
        // Constante literal para no depender del nivel de API con el que se compila la referencia.
        const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
