package com.ikeralvaro.phonecleaner.feature.apps

import com.ikeralvaro.phonecleaner.core.util.DAY_MS

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val versionName: String?,
    val installedAt: Long,
    /** Tamaños: null si falta el acceso de uso. */
    val appBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val lastUsedAt: Long?,
    val foregroundMs24h: Long,
    val ignoresBatteryOptimizations: Boolean,
) {
    val totalBytes: Long?
        get() = if (appBytes == null && dataBytes == null) null else (appBytes ?: 0L) + (dataBytes ?: 0L)
}

enum class AppSort { SIZE, CACHE, USAGE, UNUSED, RECENT }

/** Lógica pura del gestor de apps. */
object AppsLogic {
    const val UNUSED_AFTER_MS = 30 * DAY_MS

    /** Sin usar en 30 días: instalada hace más de 30 días y sin uso registrado en ese tiempo. */
    fun isUnused(app: AppEntry, now: Long): Boolean {
        if (now - app.installedAt < UNUSED_AFTER_MS) return false
        val last = app.lastUsedAt ?: return true
        return now - last > UNUSED_AFTER_MS
    }

    fun sort(apps: List<AppEntry>, sort: AppSort, now: Long): List<AppEntry> = when (sort) {
        AppSort.SIZE -> apps.sortedWith(compareByDescending<AppEntry> { it.totalBytes ?: -1L }.thenBy { it.label.lowercase() })
        AppSort.CACHE -> apps.sortedWith(compareByDescending<AppEntry> { it.cacheBytes ?: -1L }.thenBy { it.label.lowercase() })
        AppSort.USAGE -> apps.sortedWith(compareByDescending<AppEntry> { it.foregroundMs24h }.thenBy { it.label.lowercase() })
        AppSort.RECENT -> apps.sortedWith(compareByDescending<AppEntry> { it.lastUsedAt ?: 0L }.thenBy { it.label.lowercase() })
        AppSort.UNUSED -> apps.filter { isUnused(it, now) }
            .sortedWith(compareBy<AppEntry> { it.lastUsedAt ?: 0L }.thenByDescending { it.totalBytes ?: 0L })
    }

    /** Apps candidatas a detener: usadas en las últimas [windowMs], sin la propia app, el launcher ni el teclado. */
    fun boostCandidates(
        apps: List<AppEntry>,
        now: Long,
        windowMs: Long,
        excluded: Set<String>,
    ): List<AppEntry> = apps
        .filter { it.packageName !in excluded }
        .filter { (it.lastUsedAt ?: 0L) >= now - windowMs }
        .sortedByDescending { it.lastUsedAt ?: 0L }
}
