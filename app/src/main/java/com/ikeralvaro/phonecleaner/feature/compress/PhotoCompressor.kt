package com.ikeralvaro.phonecleaner.feature.compress

import android.Manifest
import android.app.ActivityManager
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
import kotlinx.coroutines.CancellationException
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

    /** Límite de píxeles a decodificar: más bajo en móviles con poca RAM. */
    private val maxPixels: Long by lazy {
        val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        if (lowRam) LOW_RAM_MAX_PIXELS else CompressionPlanner.MAX_PHOTO_PIXELS
    }

    suspend fun compress(item: MediaItem, mode: CompressionMode): CompressionOutcome = withContext(Dispatchers.IO) {
        val uri = originalUri(item.uri.toUri())
        val temp = File(context.cacheDir, "compress/${item.id}.jpg").apply { parentFile?.mkdirs() }
        var bitmap: Bitmap? = null
        try {
            // Primero solo las dimensiones reales (MediaStore puede no tenerlas o estar mal).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext CompressionOutcome.Failed("bounds")
            if (pixels > maxPixels) return@withContext CompressionOutcome.Failed("too_large")
            // Las «fotos en movimiento» llevan un vídeo dentro del JPEG: recomprimir lo perdería.
            if (isMotionPhoto(uri, item.name)) return@withContext CompressionOutcome.NotWorthIt

            bitmap = try {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: OutOfMemoryError) {
                null
            } ?: return@withContext CompressionOutcome.Failed("decode")

            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, CompressionPlanner.photoQuality(mode), it) }
            bitmap.recycle()
            bitmap = null

            if (!CompressionPlanner.isWorthIt(item.sizeBytes, temp.length(), CompressionPlanner.PHOTO_MIN_SAVINGS)) {
                return@withContext CompressionOutcome.NotWorthIt
            }
            copyExif(uri, temp)
            val newUri = saver.save(temp, item, "image/jpeg", isVideo = false)
                ?: return@withContext CompressionOutcome.Failed("save")
            CompressionOutcome.Saved(item.sizeBytes - temp.length(), newUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CompressionOutcome.Failed(e.javaClass.simpleName)
        } finally {
            bitmap?.recycle()
            temp.delete()
        }
    }

    /** Motion Photo / Micro Video de Google, Samsung y otros: lo indica el XMP (o el nombre MVIMG_). */
    private fun isMotionPhoto(uri: Uri, name: String): Boolean {
        if (name.startsWith("MVIMG_", ignoreCase = true)) return true
        val xmp = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_XMP) }
        } catch (_: Exception) {
            null
        } ?: return false
        return isMotionPhotoXmp(xmp)
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

    companion object {
        /** En móviles con poca RAM no se decodifican fotos de más de 16 MP. */
        const val LOW_RAM_MAX_PIXELS = 16_000_000L

        fun isMotionPhotoXmp(xmp: String): Boolean =
            "MotionPhoto" in xmp || "MicroVideo" in xmp || "GCamera:MotionPhoto" in xmp

        private val EXIF_TAGS = listOf(
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
