package com.ikeralvaro.phonecleaner.feature.cleaner

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import com.ikeralvaro.phonecleaner.feature.apps.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

data class JunkItem(
    val path: String,
    val label: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
)

data class ScanProgress(val filesScanned: Int, val bytesFound: Long, val currentPath: String)

/**
 * Recorre el almacenamiento compartido buscando basura según [JunkRules].
 * Necesita acceso a todos los archivos (Android 11+) o los permisos de almacenamiento (Android 8–10).
 */
class JunkScanner(
    private val context: Context,
    private val apps: AppRepository,
    private val root: File = Environment.getExternalStorageDirectory(),
) {
    suspend fun scan(onProgress: (ScanProgress) -> Unit): Map<JunkCategory, List<JunkItem>> = withContext(Dispatchers.IO) {
        val walker = Walker(System.currentTimeMillis(), onProgress)
        walker.visitDir(root, "")
        walker.addOwnCache()
        walker.results.mapValues { (_, items) -> items.sortedByDescending { it.sizeBytes } }
    }

    /** Borra los elementos y devuelve los bytes realmente liberados. */
    suspend fun delete(items: Collection<JunkItem>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val deletedPaths = mutableListOf<String>()
        for (item in items) {
            currentCoroutineContext().ensureActive()
            val file = File(item.path)
            if (!file.exists()) continue
            val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (ok) {
                freed += item.sizeBytes
                deletedPaths += item.path
            }
        }
        // En Android 10 o inferior el índice multimedia no se entera solo de los borrados.
        if (Build.VERSION.SDK_INT < 30 && deletedPaths.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, deletedPaths.toTypedArray(), null, null)
        }
        freed
    }

    private inner class Walker(private val now: Long, private val onProgress: (ScanProgress) -> Unit) {
        val results = mutableMapOf<JunkCategory, MutableList<JunkItem>>()
        private var filesScanned = 0
        private var bytesFound = 0L

        private fun add(category: JunkCategory, item: JunkItem) {
            results.getOrPut(category) { mutableListOf() } += item
            bytesFound += item.sizeBytes
        }

        /** Devuelve true si la carpeta no contiene ningún archivo y se puede borrar entera. */
        suspend fun visitDir(dir: File, rel: String): Boolean {
            currentCoroutineContext().ensureActive()
            val children = dir.listFiles() ?: return false
            var empty = true
            val emptyChildren = mutableListOf<File>()
            for (child in children) {
                val childRel = if (rel.isEmpty()) child.name else "$rel/${child.name}"
                if (child.isDirectory) {
                    if (isSymlink(child) || JunkRules.shouldSkipDir(childRel)) {
                        empty = false
                        continue
                    }
                    if (JunkRules.isThumbnailDir(childRel)) {
                        val size = dirSize(child)
                        if (size > 0) add(JunkCategory.THUMBNAILS, JunkItem(child.path, childRel, size, isDirectory = true))
                        empty = false
                        continue
                    }
                    val residual = JunkRules.residualPackage(childRel)
                    if (residual != null && !JunkRules.isSystemPackage(residual) && !apps.isInstalled(residual)) {
                        add(JunkCategory.RESIDUAL_FOLDERS, JunkItem(child.path, residual, dirSize(child), isDirectory = true))
                        empty = false
                        continue
                    }
                    val childEmpty = visitDir(child, childRel)
                    when {
                        childEmpty && JunkRules.isDeletableEmptyDir(childRel) -> emptyChildren += child
                        childEmpty -> empty = false
                        else -> empty = false
                    }
                } else {
                    empty = false
                    visitFile(child, childRel)
                }
            }
            if (empty && JunkRules.isDeletableEmptyDir(rel)) return true
            // Esta carpeta se queda: sus subcarpetas vacías sí son basura.
            emptyChildren.forEach { add(JunkCategory.EMPTY_FOLDERS, JunkItem(it.path, relativeOf(it), 0L, isDirectory = true)) }
            return false
        }

        private fun visitFile(file: File, rel: String) {
            filesScanned++
            if (filesScanned % 250 == 0) onProgress(ScanProgress(filesScanned, bytesFound, rel))
            var category = JunkRules.classifyFile(rel, file.lastModified(), now) ?: return
            if (category == JunkCategory.APK_OTHER) {
                val info = apps.apkInfo(file.path)
                val installed = info?.let { apps.installedVersionCode(it.first) }
                if (info != null && installed != null && installed >= info.second) category = JunkCategory.APK_INSTALLED
            }
            add(category, JunkItem(file.path, rel, file.length(), isDirectory = false))
        }

        fun addOwnCache() {
            listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
                dir.listFiles()?.forEach { child ->
                    val size = if (child.isDirectory) dirSize(child) else child.length()
                    if (size > 0) add(JunkCategory.TEMP_FILES, JunkItem(child.path, "Phone Cleaner/${child.name}", size, child.isDirectory))
                }
            }
            onProgress(ScanProgress(filesScanned, bytesFound, ""))
        }

        private fun relativeOf(file: File) = file.path.removePrefix(root.path).trim('/')
    }

    private fun isSymlink(file: File) = try {
        Files.isSymbolicLink(file.toPath())
    } catch (_: Exception) {
        true
    }

    private fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
