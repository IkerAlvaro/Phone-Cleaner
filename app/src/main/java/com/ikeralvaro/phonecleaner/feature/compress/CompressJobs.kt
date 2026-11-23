package com.ikeralvaro.phonecleaner.feature.compress

import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import java.io.File

/**
 * Lista de elementos a comprimir guardada en un archivo (los datos de WorkManager están limitados a 10 KB) y
 * registro de originales ya comprimidos. Los textos se escapan, así que nombres con tabuladores o saltos de
 * línea se recuperan tal cual.
 *
 * El progreso se guarda elemento a elemento: al terminar uno se añade a los originales y se quita del trabajo.
 * Si el proceso muere y WorkManager reintenta, solo quedan los pendientes y nunca se guarda un duplicado.
 */
object CompressJobs {
    private const val SEP = "\t"

    fun write(file: File, items: List<MediaItem>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(items.joinToString("\n", transform = ::encode))
        // Escritura atómica: si se corta a mitad, el archivo anterior sigue entero.
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun read(file: File): List<MediaItem> =
        if (!file.exists()) emptyList() else file.readLines().filter { it.isNotEmpty() }.mapNotNull(::decode)

    /** Quita del trabajo el elemento ya procesado (con éxito o no) para no repetirlo en un reintento. */
    fun remove(file: File, id: Long) {
        val remaining = read(file).filter { it.id != id }
        write(file, remaining)
    }

    /** Añade un original comprimido y lo ahorrado al registro de esta ejecución. */
    fun appendOriginal(file: File, uri: String, savedBytes: Long) {
        file.parentFile?.mkdirs()
        file.appendText(escape(uri) + SEP + savedBytes + "\n")
    }

    /** Originales comprimidos (URI y bytes ahorrados) de una ejecución. */
    fun readOriginals(file: File): List<Pair<String, Long>> = if (!file.exists()) {
        emptyList()
    } else {
        file.readLines().filter { it.isNotBlank() }.map { line ->
            val parts = line.split(SEP)
            unescape(parts[0]) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        }
    }

    internal fun encode(item: MediaItem): String = listOf(
        item.id, escape(item.uri), escape(item.name), item.sizeBytes, item.width, item.height, item.dateTakenMs,
        escape(item.mimeType), escape(item.relativePath), escape(item.path.orEmpty()), item.durationMs,
    ).joinToString(SEP)

    internal fun decode(line: String): MediaItem? {
        val f = line.split(SEP)
        if (f.size < 11) return null
        return MediaItem(
            id = f[0].toLongOrNull() ?: return null,
            uri = unescape(f[1]),
            name = unescape(f[2]),
            sizeBytes = f[3].toLongOrNull() ?: 0L,
            width = f[4].toIntOrNull() ?: 0,
            height = f[5].toIntOrNull() ?: 0,
            dateTakenMs = f[6].toLongOrNull() ?: 0L,
            mimeType = unescape(f[7]),
            relativePath = unescape(f[8]),
            path = unescape(f[9]).ifEmpty { null },
            durationMs = f[10].toLongOrNull() ?: 0L,
        )
    }

    internal fun escape(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    internal fun unescape(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
