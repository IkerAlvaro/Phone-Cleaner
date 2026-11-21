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
            name in TEMP_NAMES || extension in TEMP_EXTENSIONS -> JunkCategory.TEMP_FILES
            extension in APK_EXTENSIONS -> JunkCategory.APK_OTHER
            segs.size == 2 && segs[0].lowercase() in DOWNLOAD_DIRS &&
                lastModifiedMs > 0 && nowMs - lastModifiedMs > OLD_DOWNLOAD_AGE_MS -> JunkCategory.OLD_DOWNLOADS
            else -> null
        }
    }

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
