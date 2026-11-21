package com.ikeralvaro.phonecleaner.feature.apps

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.ikeralvaro.phonecleaner.core.permissions.PermissionManager
import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lista las apps con icono (visibles mediante <queries>, sin QUERY_ALL_PACKAGES) con su tamaño, uso y
 * estado de batería. Los tamaños y el uso necesitan el acceso de uso.
 */
class AppRepository(private val context: Context, private val permissions: PermissionManager) {
    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(only: String? = null): List<AppEntry> = withContext(Dispatchers.IO) {
        val hasUsage = permissions.hasUsageAccess()
        val now = System.currentTimeMillis()
        val usage24h = if (hasUsage) usageSince(now - DAY_MS, now) else emptyMap()
        val usage30d = if (hasUsage) usageSince(now - 30 * DAY_MS, now) else emptyMap()
        val power = context.getSystemService(PowerManager::class.java)
        val storageStats = context.getSystemService(StorageStatsManager::class.java)

        val packages = if (only != null) listOf(only) else launchablePackages()
        packages.mapNotNull { pkg ->
            val info = packageInfo(pkg) ?: return@mapNotNull null
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val stats = if (hasUsage) {
                try {
                    storageStats.queryStatsForPackage(appInfo.storageUuid, pkg, Process.myUserHandle())
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            val u30 = usage30d[pkg]
            val u24 = usage24h[pkg]
            AppEntry(
                packageName = pkg,
                label = appInfo.loadLabel(pm).toString(),
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                versionName = info.versionName,
                installedAt = info.firstInstallTime,
                appBytes = stats?.appBytes,
                dataBytes = stats?.let { it.dataBytes },
                cacheBytes = stats?.cacheBytes,
                lastUsedAt = listOfNotNull(u30?.lastUsed, u24?.lastUsed).maxOrNull()?.takeIf { it > 0 },
                foregroundMs24h = u24?.foregroundMs ?: 0L,
                ignoresBatteryOptimizations = power.isIgnoringBatteryOptimizations(pkg),
            )
        }
    }

    suspend fun loadApp(packageName: String): AppEntry? = loadApps(only = packageName).firstOrNull()

    fun isInstalled(packageName: String): Boolean = packageInfo(packageName) != null

    fun installedVersionCode(packageName: String): Long? =
        packageInfo(packageName)?.let { PackageInfoCompat.getLongVersionCode(it) }

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

    private fun launchablePackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return activities.map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }
    }

    private fun packageInfo(pkg: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private data class Usage(val lastUsed: Long, val foregroundMs: Long)

    private fun usageSince(start: Long, end: Long): Map<String, Usage> = try {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        usm.queryAndAggregateUsageStats(start, end).mapValues { (_, s) ->
            val visible = if (Build.VERSION.SDK_INT >= 29) s.lastTimeVisible else 0L
            Usage(maxOf(s.lastTimeUsed, visible), s.totalTimeInForeground)
        }
    } catch (_: Exception) {
        emptyMap()
    }
}
