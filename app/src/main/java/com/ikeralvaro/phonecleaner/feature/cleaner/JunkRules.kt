package com.ikeralvaro.phonecleaner.feature.cleaner

import com.ikeralvaro.phonecleaner.core.util.DAY_MS

/** Categorías de basura. [preselected] indica si se marca por defecto (las dudosas se revisan a mano). */
enum class JunkCategory(val preselected: Boolean) {
    THUMBNAILS(true),
    TEMP_FILES(true),
    EMPTY_FOLDERS(true),
    APK_INSTALLED(true),
    APK_OTHER(false),
    OLD_DOWNLOADS(false),
    RESIDUAL_FOLDERS(false),
}

/**
 * Reglas puras para decidir qué es basura a partir de la ruta relativa a la raíz del almacenamiento
 * ("DCIM/.thumbnails/123.jpg"). No tocan el disco, así que se prueban en tests unitarios.
 */
object JunkRules {
    const val OLD_DOWNLOAD_AGE_MS = 90 * DAY_MS

    /** Un temporal o log fuera de una carpeta de caché solo se considera basura a partir de esta antigüedad. */
    const val TEMP_MIN_AGE_MS = 7 * DAY_MS

    /** Una carpeta de una app que no se ve instalada solo es un "posible resto" si lleva este tiempo sin cambios. */
    const val RESIDUAL_MIN_AGE_MS = 30 * DAY_MS

    private val CACHE_DIR_NAMES = setOf("cache", ".cache", "temp", "tmp")
    private val USER_DIRS = setOf("documents", "download", "downloads")

    private val SKIPPED_DIRS = setOf("android/data", "android/obb", "lost.dir")

    /** Carpetas estándar que nunca se borran aunque estén vacías. */
    private val PROTECTED_DIRS = setOf(
        "android", "android/media", "alarms", "audiobooks", "dcim", "dcim/camera", "dcim/screenshots",
        "documents", "download", "downloads", "movies", "music", "notifications", "pictures",
        "pictures/screenshots", "podcasts", "recordings", "ringtones",
    )

    private val DOWNLOAD_DIRS = setOf("download", "downloads")
    private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "dmp", "chk")
    private val TEMP_NAMES = setOf("thumbs.db", ".ds_store", "desktop.ini")
    private val APK_EXTENSIONS = setOf("apk", "apks", "xapk")

    private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$")

    private val SYSTEM_PREFIXES = listOf(
        "android.", "com.android.", "com.google.android.", "com.samsung.", "com.sec.", "com.miui.",
        "com.xiaomi.", "com.huawei.", "com.hihonor.", "com.oneplus.", "com.oplus.", "com.oppo.",
        "com.coloros.", "com.vivo.", "com.motorola.", "com.lge.", "com.sonymobile.", "com.qualcomm.",
    )

    fun normalize(relativePath: String): String = relativePath.replace('\\', '/').trim('/')

    private fun segments(relativePath: String) = normalize(relativePath).split('/').filter { it.isNotEmpty() }

    /** Carpetas que no se recorren: Android las protege o no deben tocarse. */
    fun shouldSkipDir(relativePath: String): Boolean = normalize(relativePath).lowercase() in SKIPPED_DIRS

    fun isThumbnailDir(relativePath: String): Boolean =
        segments(relativePath).lastOrNull()?.equals(".thumbnails", ignoreCase = true) == true

    /** Una carpeta vacía se puede borrar si no es estándar ni oculta (las apps usan carpetas ocultas para configuración). */
    fun isDeletableEmptyDir(relativePath: String): Boolean {
        val segs = segments(relativePath)
        if (segs.isEmpty()) return false
        if (segs.any { it.startsWith(".") }) return false
        return normalize(relativePath).lowercase() !in PROTECTED_DIRS
    }

    /** Clasifica un archivo. Los APK se devuelven como [JunkCategory.APK_OTHER]; el escáner comprueba si están instalados. */
    fun classifyFile(relativePath: String, lastModifiedMs: Long, nowMs: Long): JunkCategory? {
        val segs = segments(relativePath)
        if (segs.isEmpty()) return null
        if (segs.dropLast(1).any { it.equals(".thumbnails", ignoreCase = true) }) return JunkCategory.THUMBNAILS
        val name = segs.last().lowercase()
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        return when {
            name in TEMP_NAMES -> JunkCategory.TEMP_FILES
            extension in TEMP_EXTENSIONS ->
                if (isOldEnough(lastModifiedMs, nowMs, TEMP_MIN_AGE_MS) ||
                    (segs[0].lowercase() !in USER_DIRS && isInCacheDir(segs))
                ) {
                    JunkCategory.TEMP_FILES
                } else {
                    null
                }
            extension in APK_EXTENSIONS -> JunkCategory.APK_OTHER
            segs.size == 2 && segs[0].lowercase() in DOWNLOAD_DIRS &&
                lastModifiedMs > 0 && nowMs - lastModifiedMs > OLD_DOWNLOAD_AGE_MS -> JunkCategory.OLD_DOWNLOADS
            else -> null
        }
    }

    /** Algún segmento de carpeta (no el nombre del archivo) es tipo caché: "cache", ".cache", "temp" o "tmp". */
    private fun isInCacheDir(segs: List<String>): Boolean = segs.dropLast(1).any { it.lowercase() in CACHE_DIR_NAMES }

    private fun isOldEnough(lastModifiedMs: Long, nowMs: Long, minAgeMs: Long): Boolean =
        lastModifiedMs > 0 && nowMs - lastModifiedMs >= minAgeMs

    /**
     * Una carpeta de un paquete que no aparece instalado puede ser de una app sin icono (invisible para nosotros):
     * solo se propone como posible resto si lleva [RESIDUAL_MIN_AGE_MS] sin modificarse.
     */
    fun isPossibleLeftover(lastModifiedMs: Long, nowMs: Long): Boolean =
        isOldEnough(lastModifiedMs, nowMs, RESIDUAL_MIN_AGE_MS)

    /** Si la ruta es "Android/media/<paquete>", devuelve el paquete. */
    fun residualPackage(relativePath: String): String? {
        val segs = segments(relativePath)
        if (segs.size != 3) return null
        if (!segs[0].equals("android", ignoreCase = true) || !segs[1].equals("media", ignoreCase = true)) return null
        return segs[2].takeIf { PACKAGE_REGEX.matches(it) }
    }

    /**
     * Paquetes de sistema o del fabricante. La app solo "ve" las apps con icono, así que un paquete de sistema
     * sin icono parecería desinstalado: estos nunca se consideran restos.
     */
    fun isSystemPackage(packageName: String): Boolean =
        packageName == "android" || SYSTEM_PREFIXES.any { packageName.startsWith(it) }
}
