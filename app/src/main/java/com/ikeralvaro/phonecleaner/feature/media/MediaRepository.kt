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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Resultado de preparar un borrado: o hace falta la confirmación del sistema, o ya está hecho.
 *
 * Borrado en dos pasos (Android 11+): [MediaRepository.prepareDelete] NO borra nada si hay fotos o vídeos que
 * requieren confirmación; devuelve [NeedsConfirmation] con los archivos no multimedia en [NeedsConfirmation.deferred].
 * Cuando el sistema responde, se llama a MediaViewModel.finishDelete, que borra esos archivos solo si el usuario
 * confirmó.
 */
sealed interface DeleteRequest {
    /**
     * [pendingIds]: fotos y vídeos de esta petición (como mucho [MediaRepository.TRASH_BATCH]; si había más, se
     * quedan seleccionados para otra pasada). [deferred]: archivos no multimedia que se borrarán al confirmar.
     * [directDeletedIds]/[directFreedBytes] se mantienen por compatibilidad y ahora siempre están vacíos.
     */
    data class NeedsConfirmation(
        val sender: IntentSender,
        val pendingIds: Set<Long>,
        val directDeletedIds: Set<Long> = emptySet(),
        val directFreedBytes: Long = 0L,
        val deferred: List<MediaItem> = emptyList(),
    ) : DeleteRequest
    data class Done(val deletedIds: Set<Long>, val freedBytes: Long) : DeleteRequest
}

class MediaRepository(private val context: Context, private val permissions: PermissionManager) {
    private val resolver = context.contentResolver
    private val hashCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, Long>>() // id -> (tamaño, dHash)

    suspend fun images(): List<MediaItem> = query(imagesCollection(), "image/*", Kind.IMAGE)

    suspend fun videos(): List<MediaItem> = query(videosCollection(), "video/*", Kind.VIDEO)

    /** Archivos de cualquier tipo de más de [minBytes] (con acceso a todos los archivos incluye los no multimedia). */
    suspend fun largeFiles(minBytes: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val result = mutableListOf<MediaItem>()
        resolver.query(
            collection, projection(Kind.FILE), "${MediaStore.MediaColumns.SIZE} >= ?", arrayOf(minBytes.toString()),
            "${MediaStore.MediaColumns.SIZE} DESC",
        )?.use { c ->
            val cols = Columns(c)
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = c.getLong(cols.id)
                val uri = when (if (cols.mediaType >= 0) c.getInt(cols.mediaType) else -1) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(imagesCollection(), id)
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(videosCollection(), id)
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> ContentUris.withAppendedId(audioCollection(), id)
                    else -> ContentUris.withAppendedId(collection, id)
                }
                readItem(c, cols, uri)?.let(result::add)
            }
        }
        result.filter { it.mimeType.isNotEmpty() || it.path != null }
    }

    /** MD5 del contenido completo, para confirmar duplicados exactos. Se puede cancelar a mitad de lectura. */
    suspend fun md5(item: MediaItem): String? = digest(item) { input, digest ->
        val buffer = ByteArray(HASH_CHUNK)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }

    /**
     * Hash rápido con el primer y el último bloque de 64 KB (y el tamaño). Si dos archivos del mismo tamaño
     * difieren aquí, no hace falta leerlos enteros.
     */
    suspend fun partialHash(item: MediaItem): String? = digest(item) { input, digest ->
        val buffer = ByteArray(HASH_CHUNK)
        val first = input.readFully(buffer)
        if (first > 0) digest.update(buffer, 0, first)
        val tailStart = item.sizeBytes - HASH_CHUNK
        if (tailStart > first) {
            var toSkip = tailStart - first
            while (toSkip > 0) {
                currentCoroutineContext().ensureActive()
                val skipped = input.skip(toSkip)
                if (skipped <= 0) break
                toSkip -= skipped
            }
            val last = input.readFully(buffer)
            if (last > 0) digest.update(buffer, 0, last)
        }
        digest.update(item.sizeBytes.toString().toByteArray())
    }

    private suspend fun digest(
        item: MediaItem,
        block: suspend (InputStream, MessageDigest) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(item.uri.toUri())?.use { input ->
                val digest = MessageDigest.getInstance("MD5")
                block(input, digest)
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
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
     * en ese caso no se borra nada todavía (los archivos no multimedia esperan a [finishDelete]). Si no hace
     * falta confirmación (Android 10 o inferior, o solo archivos no multimedia) se borra directamente.
     */
    suspend fun prepareDelete(items: List<MediaItem>): DeleteRequest = withContext(Dispatchers.IO) {
        val direct = mutableListOf<MediaItem>()
        val viaSystem = mutableListOf<MediaItem>()
        for (item in items) {
            if (Build.VERSION.SDK_INT >= 30 && isMediaUri(item.uri)) viaSystem += item else direct += item
        }
        if (viaSystem.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
            // Por lotes: el sistema falla con peticiones enormes. Lo que no quepa se queda para otra pasada.
            val batch = viaSystem.take(TRASH_BATCH)
            val sender = try {
                MediaStore.createTrashRequest(resolver, batch.map { it.uri.toUri() }, true).intentSender
            } catch (_: Exception) {
                null
            } ?: return@withContext DeleteRequest.Done(emptySet(), 0L)
            return@withContext DeleteRequest.NeedsConfirmation(sender, batch.map { it.id }.toSet(), deferred = direct)
        }
        val (deleted, freed) = deleteFiles(direct)
        DeleteRequest.Done(deleted, freed)
    }

    /**
     * Segundo paso del borrado, tras la respuesta del sistema: si [confirmed], borra los archivos no multimedia
     * que esperaban; si no, no toca nada.
     */
    suspend fun finishDelete(request: DeleteRequest.NeedsConfirmation, confirmed: Boolean): DeleteRequest.Done =
        withContext(Dispatchers.IO) {
            if (!confirmed) return@withContext DeleteRequest.Done(emptySet(), 0L)
            val (deleted, freed) = deleteFiles(request.deferred)
            DeleteRequest.Done(deleted, freed)
        }

    private fun deleteFiles(items: List<MediaItem>): Pair<Set<Long>, Long> {
        var freed = 0L
        val deleted = mutableSetOf<Long>()
        val scanned = mutableListOf<String>()
        for (item in items) {
            val path = item.path ?: continue
            val file = File(path)
            if (file.exists() && file.delete()) {
                freed += item.sizeBytes
                deleted += item.id
                scanned += path
            }
        }
        if (scanned.isNotEmpty()) MediaScannerConnection.scanFile(context, scanned.toTypedArray(), null, null)
        return deleted to freed
    }

    private fun isMediaUri(uri: String) = uri.contains("/images/") || uri.contains("/video/") || uri.contains("/audio/")

    /** JPEG de al menos [minBytes] (candidatas a comprimir): se filtra en MediaStore, no en memoria. */
    suspend fun jpegImages(minBytes: Long): List<MediaItem> = query(
        imagesCollection(), "image/*", Kind.IMAGE,
        "${MediaStore.MediaColumns.MIME_TYPE} = ? AND ${MediaStore.MediaColumns.SIZE} >= ?",
        arrayOf("image/jpeg", minBytes.toString()),
    )

    /** Vídeos de al menos [minBytes]. */
    suspend fun videos(minBytes: Long): List<MediaItem> = query(
        videosCollection(), "video/*", Kind.VIDEO,
        "${MediaStore.MediaColumns.SIZE} >= ?", arrayOf(minBytes.toString()),
    )

    private enum class Kind { IMAGE, VIDEO, FILE }

    private suspend fun query(
        collection: Uri,
        mimePrefix: String,
        kind: Kind,
        selection: String? = null,
        args: Array<String>? = null,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        resolver.query(collection, projection(kind), selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val cols = Columns(c)
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = c.getLong(cols.id)
                readItem(c, cols, ContentUris.withAppendedId(collection, id), mimePrefix)?.let(result::add)
            }
        }
        result
    }

    /**
     * Columnas a pedir. DATE_TAKEN (fotos y vídeos) y DURATION (vídeos) existen desde la API 1 en sus tablas,
     * así que se piden en todas las versiones (antes solo en Android 10+ y los vídeos no funcionaban en 7–9).
     */
    private fun projection(kind: Kind): Array<String> {
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
        when (kind) {
            Kind.IMAGE -> columns += MediaStore.Images.ImageColumns.DATE_TAKEN
            Kind.VIDEO -> {
                columns += MediaStore.Video.VideoColumns.DATE_TAKEN
                columns += MediaStore.Video.VideoColumns.DURATION
            }
            Kind.FILE -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    columns += MediaStore.MediaColumns.DATE_TAKEN
                    columns += MediaStore.MediaColumns.DURATION
                }
                columns += MediaStore.Files.FileColumns.MEDIA_TYPE
            }
        }
        if (Build.VERSION.SDK_INT >= 29) columns += MediaStore.MediaColumns.RELATIVE_PATH
        return columns.distinct().toTypedArray()
    }

    /** Índices de columna calculados una vez por consulta (no en cada fila). */
    private class Columns(c: Cursor) {
        val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val name = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val size = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val width = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val height = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val mime = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val modified = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        @Suppress("DEPRECATION")
        val data = c.getColumnIndex(MediaStore.MediaColumns.DATA)
        val taken = c.getColumnIndex(COL_DATE_TAKEN)
        val duration = c.getColumnIndex(COL_DURATION)
        val relative = c.getColumnIndex(COL_RELATIVE_PATH)
        val mediaType = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
    }

    private fun readItem(c: Cursor, cols: Columns, uri: Uri, mimeFallback: String = ""): MediaItem? {
        fun str(i: Int) = if (i >= 0 && !c.isNull(i)) c.getString(i) else null
        fun long(i: Int) = if (i >= 0 && !c.isNull(i)) c.getLong(i) else null
        val size = long(cols.size) ?: return null
        if (size <= 0) return null
        val path = str(cols.data)
        val taken = long(cols.taken)
        return MediaItem(
            id = long(cols.id) ?: return null,
            uri = uri.toString(),
            name = str(cols.name) ?: path?.substringAfterLast('/') ?: "",
            sizeBytes = size,
            width = long(cols.width)?.toInt() ?: 0,
            height = long(cols.height)?.toInt() ?: 0,
            dateTakenMs = taken?.takeIf { it > 0 } ?: ((long(cols.modified) ?: 0L) * 1000),
            mimeType = str(cols.mime) ?: mimeFallback,
            relativePath = str(cols.relative) ?: path?.substringBeforeLast('/', "")?.substringAfter("/0/", "").orEmpty(),
            path = path,
            durationMs = long(cols.duration) ?: 0L,
        )
    }

    fun hasAccess(): Boolean = permissions.hasMediaAccess()

    companion object {
        /** Máximo de elementos por petición de papelera del sistema. */
        const val TRASH_BATCH = 500
        private const val HASH_CHUNK = 64 * 1024
        // Nombres de columna literales: existen en las tablas de fotos/vídeos en todas las versiones.
        private const val COL_DATE_TAKEN = "datetaken"
        private const val COL_DURATION = "duration"
        private const val COL_RELATIVE_PATH = "relative_path"

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
