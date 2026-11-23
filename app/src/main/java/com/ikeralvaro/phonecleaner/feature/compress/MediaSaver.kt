package com.ikeralvaro.phonecleaner.feature.compress

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import java.io.File

/** Guarda la versión comprimida junto al original (mismo álbum y fecha) en la galería. */
class MediaSaver(private val context: Context) {
    private val resolver = context.contentResolver

    fun save(temp: File, original: MediaItem, mimeType: String, isVideo: Boolean): Uri? =
        if (Build.VERSION.SDK_INT >= 29) saveScoped(temp, original, mimeType, isVideo) else saveLegacy(temp, original)

    @RequiresApi(29)
    private fun saveScoped(temp: File, original: MediaItem, mimeType: String, isVideo: Boolean): Uri? {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val fallbackDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val dirs = listOf(original.relativePath.ifBlank { "$fallbackDir/" }, "$fallbackDir/Phone Cleaner/").distinct()
        for (dir in dirs) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, original.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (original.dateTakenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, original.dateTakenMs)
            }
            // Algunas carpetas (p. ej. de apps) no admiten archivos nuevos: se prueba la siguiente.
            val uri = try {
                resolver.insert(collection, values)
            } catch (_: IllegalArgumentException) {
                null
            } ?: continue
            return try {
                resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                uri
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        }
        return null
    }

    private fun saveLegacy(temp: File, original: MediaItem): Uri? {
        val source = original.path?.let(::File) ?: return null
        val dir = source.parentFile ?: return null
        val target = uniqueTarget(dir, source.nameWithoutExtension, source.extension)
        return try {
            // Nunca se sobrescribe: uniqueTarget da un nombre libre y copyTo falla si apareció entretanto.
            temp.copyTo(target, overwrite = false)
            if (original.dateTakenMs > 0) target.setLastModified(original.dateTakenMs)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), null, null)
            Uri.fromFile(target)
        } catch (_: Exception) {
            null
        }
    }

    /** "foto_comp.jpg", y si existe "foto_comp_2.jpg", "foto_comp_3.jpg"... */
    private fun uniqueTarget(dir: File, base: String, extension: String): File {
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var target = File(dir, "${base}_comp$suffix")
        var n = 2
        while (target.exists()) {
            target = File(dir, "${base}_comp_$n$suffix")
            n++
        }
        return target
    }
}
