package com.ikeralvaro.phonecleaner.feature.compress

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface CompressionOutcome {
    data class Saved(val savedBytes: Long, val newUri: Uri) : CompressionOutcome
    data object NotWorthIt : CompressionOutcome
    data class Failed(val reason: String) : CompressionOutcome
}

/**
 * Recomprime fotos JPEG manteniendo resolución y metadatos (fecha, GPS, orientación, cámara).
 * BitmapFactory no aplica la rotación EXIF, así que copiar la etiqueta de orientación mantiene la foto bien girada.
 */
class PhotoCompressor(private val context: Context, private val saver: MediaSaver) {
    private val resolver = context.contentResolver

    suspend fun compress(item: MediaItem, mode: CompressionMode): CompressionOutcome = withContext(Dispatchers.IO) {
        val uri = originalUri(item.uri.toUri())
        val temp = File(context.cacheDir, "compress/${item.id}.jpg").apply { parentFile?.mkdirs() }
        try {
            val bitmap = try {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: OutOfMemoryError) {
                null
            } ?: return@withContext CompressionOutcome.Failed("decode")

            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, CompressionPlanner.photoQuality(mode), it) }
            bitmap.recycle()

            if (!CompressionPlanner.isWorthIt(item.sizeBytes, temp.length(), CompressionPlanner.PHOTO_MIN_SAVINGS)) {
                return@withContext CompressionOutcome.NotWorthIt
            }
            copyExif(uri, temp)
            val newUri = saver.save(temp, item, "image/jpeg", isVideo = false)
                ?: return@withContext CompressionOutcome.Failed("save")
            CompressionOutcome.Saved(item.sizeBytes - temp.length(), newUri)
        } catch (e: Exception) {
            CompressionOutcome.Failed(e.javaClass.simpleName)
        } finally {
            temp.delete()
        }
    }

    /** En Android 10+ la ubicación EXIF viene censurada salvo que se pida el original con ACCESS_MEDIA_LOCATION. */
    private fun originalUri(uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < 29) return uri
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) MediaStore.setRequireOriginal(uri) else uri
    }

    private fun copyExif(source: Uri, target: File) {
        val original = resolver.openInputStream(source)?.use { ExifInterface(it) } ?: return
        val copy = ExifInterface(target.path)
        EXIF_TAGS.forEach { tag -> original.getAttribute(tag)?.let { copy.setAttribute(tag, it) } }
        copy.saveAttributes()
    }

    private companion object {
        val EXIF_TAGS = listOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
        )
    }
}
