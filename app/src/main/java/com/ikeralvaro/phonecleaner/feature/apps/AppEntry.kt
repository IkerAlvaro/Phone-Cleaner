package com.ikeralvaro.phonecleaner.feature.apps

/** Datos de una app que se pueden leer sin permisos especiales. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val versionName: String?,
    val installedAt: Long,
    val updatedAt: Long,
    /** Tamaño de los APK instalados (el de los datos y la caché solo se ve en los Ajustes de la app). */
    val apkBytes: Long,
    /** Declara RECEIVE_BOOT_COMPLETED: puede arrancar sola al encender el móvil. */
    val startsAtBoot: Boolean,
    val ignoresBatteryOptimizations: Boolean,
)

enum class AppSort { SIZE, NAME, NEWEST, AUTOSTART }

/** Lógica pura del gestor de apps. */
object AppsLogic {
    fun sort(apps: List<AppEntry>, sort: AppSort): List<AppEntry> = when (sort) {
        AppSort.SIZE -> apps.sortedWith(compareByDescending<AppEntry> { it.apkBytes }.thenBy { it.label.lowercase() })
        AppSort.NAME -> apps.sortedBy { it.label.lowercase() }
        AppSort.NEWEST -> apps.sortedWith(compareByDescending<AppEntry> { it.installedAt }.thenBy { it.label.lowercase() })
        AppSort.AUTOSTART -> autostartApps(apps)
    }

    /** Apps de usuario que pueden arrancar solas: candidatas a gastar batería sin que se abran. */
    fun autostartApps(apps: List<AppEntry>): List<AppEntry> =
        apps.filter { it.startsAtBoot && !it.isSystem }
            .sortedWith(compareByDescending<AppEntry> { it.ignoresBatteryOptimizations }.thenBy { it.label.lowercase() })

    /** Suma del APK base y los APK divididos (split APKs). */
    fun apkSize(paths: List<String?>, sizeOf: (String) -> Long): Long =
        paths.filterNotNull().distinct().sumOf { sizeOf(it).coerceAtLeast(0L) }
}
