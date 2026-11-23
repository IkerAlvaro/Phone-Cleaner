package com.ikeralvaro.phonecleaner.feature.apps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lista las apps con icono (visibles mediante <queries>, sin QUERY_ALL_PACKAGES) con datos que no necesitan
 * permisos especiales: tamaño de los APK, fechas, si arrancan solas y su estado de batería.
 */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    @Volatile private var cache: List<AppEntry>? = null
    @Volatile private var cacheAt = 0L

    /** Olvida la lista guardada (p. ej. tras desinstalar apps). */
    fun invalidate() {
        cache = null
    }

    /**
     * Lista de apps. Se guarda en memoria [CACHE_MS]: al volver a una pantalla no se recalcula todo. El estado de
     * batería sí se relee siempre (es barato y cambia cuando el usuario vuelve de Ajustes).
     */
    suspend fun loadApps(only: String? = null, forceRefresh: Boolean = false): List<AppEntry> = withContext(Dispatchers.IO) {
        val power = context.getSystemService(PowerManager::class.java)
        val cached = cache
        if (only == null && !forceRefresh && cached != null && android.os.SystemClock.elapsedRealtime() - cacheAt < CACHE_MS) {
            return@withContext cached.map { it.copy(ignoresBatteryOptimizations = power.isIgnoringBatteryOptimizations(it.packageName)) }
        }
        val packages = if (only != null) listOf(only) else launchablePackageNames()
        packages.mapNotNull { pkg ->
            val info = packageInfo(pkg, PackageManager.GET_PERMISSIONS) ?: return@mapNotNull null
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            AppEntry(
                packageName = pkg,
                label = appInfo.loadLabel(pm).toString(),
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                versionName = info.versionName,
                installedAt = info.firstInstallTime,
                updatedAt = info.lastUpdateTime,
                apkBytes = AppsLogic.apkSize(listOf(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty()) { File(it).length() },
                startsAtBoot = info.requestedPermissions?.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED) == true,
                ignoresBatteryOptimizations = power.isIgnoringBatteryOptimizations(pkg),
            )
        }.also {
            if (only == null) {
                cache = it
                cacheAt = android.os.SystemClock.elapsedRealtime()
            }
        }
    }

    suspend fun loadApp(packageName: String): AppEntry? = loadApps(only = packageName).firstOrNull()

    fun isInstalled(packageName: String): Boolean = packageInfo(packageName, 0) != null

    fun installedVersionCode(packageName: String): Long? =
        packageInfo(packageName, 0)?.let { PackageInfoCompat.getLongVersionCode(it) }

    /** Paquete y versión de un archivo APK, o null si no se puede leer. */
    fun apkInfo(path: String): Pair<String, Long>? = try {
        pm.getPackageArchiveInfo(path, 0)?.let { it.packageName to PackageInfoCompat.getLongVersionCode(it) }
    } catch (_: Exception) {
        null
    }

    /** Paquetes que no conviene detener: esta app, el launcher y el teclado activos. */
    fun protectedPackages(): Set<String> {
        val result = mutableSetOf(context.packageName)
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let(result::add)
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let(result::add)
        return result
    }

    /** Paquetes con icono en el launcher, sin esta app. */
    fun launchablePackageNames(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return activities.map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }
    }

    private companion object {
        const val CACHE_MS = 30_000L
    }

    private fun packageInfo(pkg: String, flags: Int): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, flags)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
