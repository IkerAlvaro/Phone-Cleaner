package com.ikeralvaro.phonecleaner.feature.compress

import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import java.io.File

/** Lista de elementos a comprimir guardada en un archivo (los datos de WorkManager están limitados a 10 KB). */
object CompressJobs {
    private const val SEP = "\t"

    fun write(file: File, items: List<MediaItem>) {
        file.parentFile?.mkdirs()
        file.writeText(
            items.joinToString("\n") { item ->
                listOf(
                    item.id, item.uri, clean(item.name), item.sizeBytes, item.width, item.height, item.dateTakenMs,
                    item.mimeType, clean(item.relativePath), clean(item.path.orEmpty()), item.durationMs,
                ).joinToString(SEP)
            },
        )
    }

    fun read(file: File): List<MediaItem> = if (!file.exists()) emptyList() else file.readLines().mapNotNull { line ->
        val f = line.split(SEP)
        if (f.size < 11) return@mapNotNull null
        MediaItem(
            id = f[0].toLongOrNull() ?: return@mapNotNull null,
            uri = f[1],
            name = f[2],
            sizeBytes = f[3].toLongOrNull() ?: 0L,
            width = f[4].toIntOrNull() ?: 0,
            height = f[5].toIntOrNull() ?: 0,
            dateTakenMs = f[6].toLongOrNull() ?: 0L,
            mimeType = f[7],
            relativePath = f[8],
            path = f[9].ifEmpty { null },
            durationMs = f[10].toLongOrNull() ?: 0L,
        )
    }

    private fun clean(value: String) = value.replace('\t', ' ').replace('\n', ' ')
}
