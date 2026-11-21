package com.ikeralvaro.phonecleaner.feature.media

data class MediaItem(
    val id: Long,
    /** URI de contenido (content://media/...). */
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateTakenMs: Long,
    val mimeType: String,
    val relativePath: String = "",
    /** Ruta en disco si MediaStore la expone (para borrar archivos que no son fotos ni vídeos). */
    val path: String? = null,
    val durationMs: Long = 0L,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val pixels: Long get() = width.toLong() * height.toLong()
}

/** Grupo de fotos repetidas o parecidas; [keeperId] es la que se propone conservar. */
data class MediaGroup(val items: List<MediaItem>, val keeperId: Long) {
    val removableBytes: Long get() = items.filter { it.id != keeperId }.sumOf { it.sizeBytes }
}

enum class MediaCategory { DUPLICATES, SIMILAR, SCREENSHOTS, LARGE_VIDEOS, LARGE_FILES }
