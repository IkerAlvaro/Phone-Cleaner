package com.ikeralvaro.phonecleaner.feature.media

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.ikeralvaro.phonecleaner.core.permissions.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Resultado de preparar un borrado: o hace falta la confirmación del sistema, o ya está hecho. */
sealed interface DeleteRequest {
    data class NeedsConfirmation(val sender: IntentSender, val pendingIds: Set<Long>, val freedSoFar: Long) : DeleteRequest
    data class Done(val deletedIds: Set<Long>, val freedBytes: Long) : DeleteRequest
}

class MediaRepository(private val context: Context, private val permissions: PermissionManager) {
    private val resolver = context.contentResolver
    private val hashCache = HashMap<Long, Pair<Long, Long>>() // id -> (tamaño, dHash)

    suspend fun images(): List<MediaItem> = query(imagesCollection(), "image/*")

    suspend fun videos(): List<MediaItem> = query(videosCollection(), "video/*")

    /** Archivos de cualquier tipo de más de [minBytes] (con acceso a todos los archivos incluye los no multimedia). */
    suspend fun largeFiles(minBytes: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = baseProjection() + MediaStore.Files.FileColumns.MEDIA_TYPE
        val result = mutableListOf<MediaItem>()
        resolver.query(
            collection, projection, "${MediaStore.MediaColumns.SIZE} >= ?", arrayOf(minBytes.toString()),
            "${MediaStore.MediaColumns.SIZE} DESC",
        )?.use { c ->
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val uri = when (c.getInt(typeCol)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(imagesCollection(), id)
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(videosCollection(), id)
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> ContentUris.withAppendedId(audioCollection(), id)
                    else -> ContentUris.withAppendedId(collection, id)
                }
                readItem(c, uri)?.let(result::add)
            }
        }
        result.filter { it.mimeType.isNotEmpty() || it.path != null }
    }

    /** MD5 del contenido completo, para confirmar duplicados exactos. */
    fun md5(item: MediaItem): String? = try {
        resolver.openInputStream(item.uri.toUri())?.use { input ->
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) {
        null
    }

    /** dHash a partir de una miniatura; se guarda en memoria mientras la app está abierta. */
    fun perceptualHash(item: MediaItem): Long? {
        hashCache[item.id]?.let { (size, hash) -> if (size == item.sizeBytes) return hash }
        val bitmap = thumbnail(item) ?: return null
        return try {
            val small = bitmap.scale(PerceptualHash.WIDTH, PerceptualHash.HEIGHT)
            val pixels = IntArray(PerceptualHash.WIDTH * PerceptualHash.HEIGHT)
            small.getPixels(pixels, 0, PerceptualHash.WIDTH, 0, 0, PerceptualHash.WIDTH, PerceptualHash.HEIGHT)
            if (small !== bitmap) small.recycle()
            PerceptualHash.dHash(PerceptualHash.luminance(pixels)).also { hashCache[item.id] = item.sizeBytes to it }
        } finally {
            bitmap.recycle()
        }
    }

    private fun thumbnail(item: MediaItem): Bitmap? = try {
        val uri = item.uri.toUri()
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.loadThumbnail(uri, Size(64, 64), null)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Prepara el borrado. En Android 11+ las fotos y vídeos van a la papelera del sistema con su confirmación;
     * el resto (o en Android 10 o inferior) se borra directamente si hay permiso.
     */
    suspend fun prepareDelete(items: List<MediaItem>): DeleteRequest = withContext(Dispatchers.IO) {
        val direct = mutableListOf<MediaItem>()
        val viaSystem = mutableListOf<MediaItem>()
        for (item in items) {
            val isMedia = item.uri.contains("/images/") || item.uri.contains("/video/") || item.uri.contains("/audio/")
            if (Build.VERSION.SDK_INT >= 30 && isMedia) viaSystem += item else direct += item
        }
        var freed = 0L
        val deleted = mutableSetOf<Long>()
        val scanned = mutableListOf<String>()
        for (item in direct) {
            val path = item.path ?: continue
            val file = File(path)
            if (file.exists() && file.delete()) {
                freed += item.sizeBytes
                deleted += item.id
                scanned += path
            }
        }
        if (scanned.isNotEmpty()) MediaScannerConnection.scanFile(context, scanned.toTypedArray(), null, null)
        if (viaSystem.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
            val uris = viaSystem.map { it.uri.toUri() }
            val pending = MediaStore.createTrashRequest(resolver, uris, true)
            DeleteRequest.NeedsConfirmation(pending.intentSender, viaSystem.map { it.id }.toSet(), freed)
        } else {
            DeleteRequest.Done(deleted, freed)
        }
    }

    private suspend fun query(collection: Uri, mimePrefix: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        resolver.query(collection, baseProjection(), null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                readItem(c, ContentUris.withAppendedId(collection, id), mimePrefix)?.let(result::add)
            }
        }
        result
    }

    private fun baseProjection(): Array<String> {
        val columns = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            columns += MediaStore.MediaColumns.DATE_TAKEN
            columns += MediaStore.MediaColumns.RELATIVE_PATH
            columns += MediaStore.MediaColumns.DURATION
        }
        return columns.toTypedArray()
    }

    private fun readItem(c: Cursor, uri: Uri, mimeFallback: String = ""): MediaItem? {
        fun str(col: String) = c.getColumnIndex(col).takeIf { it >= 0 }?.let { c.getString(it) }
        fun long(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
        val size = long(MediaStore.MediaColumns.SIZE) ?: return null
        if (size <= 0) return null
        @Suppress("DEPRECATION")
        val path = str(MediaStore.MediaColumns.DATA)
        val relative = if (Build.VERSION.SDK_INT >= 29) str(MediaStore.MediaColumns.RELATIVE_PATH) else null
        val taken = if (Build.VERSION.SDK_INT >= 29) long(MediaStore.MediaColumns.DATE_TAKEN) else null
        return MediaItem(
            id = long(MediaStore.MediaColumns._ID) ?: return null,
            uri = uri.toString(),
            name = str(MediaStore.MediaColumns.DISPLAY_NAME) ?: path?.substringAfterLast('/') ?: "",
            sizeBytes = size,
            width = long(MediaStore.MediaColumns.WIDTH)?.toInt() ?: 0,
            height = long(MediaStore.MediaColumns.HEIGHT)?.toInt() ?: 0,
            dateTakenMs = taken?.takeIf { it > 0 } ?: ((long(MediaStore.MediaColumns.DATE_MODIFIED) ?: 0L) * 1000),
            mimeType = str(MediaStore.MediaColumns.MIME_TYPE) ?: mimeFallback,
            relativePath = relative ?: path?.substringBeforeLast('/', "")?.substringAfter("/0/", "").orEmpty(),
            path = path,
            durationMs = if (Build.VERSION.SDK_INT >= 29) long(MediaStore.MediaColumns.DURATION) ?: 0L else 0L,
        )
    }

    fun hasAccess(): Boolean = permissions.hasMediaAccess()

    companion object {
        fun imagesCollection(): Uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        fun videosCollection(): Uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        fun audioCollection(): Uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }
}
