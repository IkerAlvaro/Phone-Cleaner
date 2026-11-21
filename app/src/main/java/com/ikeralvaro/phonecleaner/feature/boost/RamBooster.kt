package com.ikeralvaro.phonecleaner.feature.boost

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.ikeralvaro.phonecleaner.core.system.MemoryReader
import com.ikeralvaro.phonecleaner.core.util.HOUR_MS
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import com.ikeralvaro.phonecleaner.feature.apps.AppRepository
import com.ikeralvaro.phonecleaner.feature.apps.AppsLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Libera RAM de forma honesta: mide la memoria libre antes y después y devuelve la diferencia real.
 * En Android 14+ el sistema solo deja cerrar la propia app, así que se usa la detención rápida guiada.
 */
class RamBooster(
    private val context: Context,
    private val apps: AppRepository,
    private val memory: MemoryReader,
) {
    val canStopOtherApps: Boolean get() = Build.VERSION.SDK_INT < 34

    suspend fun candidates(hasUsageAccess: Boolean): List<AppEntry> {
        val all = apps.loadApps()
        val excluded = apps.protectedPackages()
        return if (hasUsageAccess) {
            AppsLogic.boostCandidates(all, System.currentTimeMillis(), RECENT_WINDOW_MS, excluded)
        } else {
            // Sin datos de uso: todas las apps de usuario (killBackgroundProcesses no toca las que estén en primer plano).
            all.filter { !it.isSystem && it.packageName !in excluded }.sortedBy { it.label.lowercase() }
        }
    }

    /** Android 13 o inferior: detiene los procesos en segundo plano y devuelve los bytes liberados. */
    suspend fun stopBackground(packages: List<String>): Long = withContext(Dispatchers.Default) {
        val am = context.getSystemService(ActivityManager::class.java)
        val before = memory.read().availableBytes
        packages.forEach { pkg ->
            try {
                @Suppress("DEPRECATION")
                am.killBackgroundProcesses(pkg)
            } catch (_: SecurityException) {
            }
        }
        delay(SETTLE_MS)
        (memory.read().availableBytes - before).coerceAtLeast(0L)
    }

    fun availableBytes(): Long = memory.read().availableBytes

    companion object {
        const val RECENT_WINDOW_MS = 3 * HOUR_MS
        const val SETTLE_MS = 2_000L
    }
}
