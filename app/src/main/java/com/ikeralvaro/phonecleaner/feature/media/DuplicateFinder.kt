package com.ikeralvaro.phonecleaner.feature.media

import kotlin.math.abs

/** Agrupación pura de duplicados exactos y fotos similares. */
object DuplicateFinder {
    const val SIMILAR_MAX_DISTANCE = 6
    const val SIMILAR_MAX_TIME_GAP_MS = 2 * 60 * 60 * 1000L

    /** Se conserva la de mayor resolución; si empatan, la más reciente; si no, la de menor id (la original). */
    fun chooseKeeper(items: List<MediaItem>): MediaItem = items.sortedWith(
        compareByDescending<MediaItem> { it.pixels }
            .thenByDescending { it.dateTakenMs }
            .thenBy { it.id },
    ).first()

    /**
     * Duplicados exactos: primero por tamaño (barato) y solo dentro de cada tamaño repetido se calcula
     * el hash del contenido con [contentHash].
     */
    fun exactDuplicates(items: List<MediaItem>, contentHash: (MediaItem) -> String?): List<MediaGroup> =
        items.groupBy { it.sizeBytes }
            .filter { (size, group) -> size > 0 && group.size > 1 }
            .values
            .flatMap { sameSize ->
                sameSize.mapNotNull { item -> contentHash(item)?.let { it to item } }
                    .groupBy({ it.first }, { it.second })
                    .values
                    .filter { it.size > 1 }
            }
            .map { group -> MediaGroup(group.sortedBy { it.id }, chooseKeeper(group).id) }
            .sortedByDescending { it.removableBytes }

    /**
     * Fotos similares: hash perceptual a distancia ≤ [maxDistance] y tomadas con menos de [maxTimeGapMs]
     * de diferencia (ráfagas, varias tomas de la misma escena). Une grupos transitivamente.
     */
    fun similarGroups(
        items: List<MediaItem>,
        hashes: Map<Long, Long>,
        maxDistance: Int = SIMILAR_MAX_DISTANCE,
        maxTimeGapMs: Long = SIMILAR_MAX_TIME_GAP_MS,
    ): List<MediaGroup> {
        val hashed = items.filter { it.id in hashes }.sortedBy { it.dateTakenMs }
        val parent = IntArray(hashed.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in hashed.indices) {
            val hi = hashes.getValue(hashed[i].id)
            var j = i + 1
            // Ordenadas por fecha: en cuanto se pasa la ventana de tiempo, se deja de comparar.
            while (j < hashed.size && abs(hashed[j].dateTakenMs - hashed[i].dateTakenMs) <= maxTimeGapMs) {
                if (PerceptualHash.distance(hi, hashes.getValue(hashed[j].id)) <= maxDistance) {
                    parent[find(j)] = find(i)
                }
                j++
            }
        }
        return hashed.indices.groupBy(::find)
            .values
            .filter { it.size > 1 }
            .map { indices ->
                val group = indices.map { hashed[it] }
                MediaGroup(group, chooseKeeper(group).id)
            }
            .sortedByDescending { it.removableBytes }
    }

    fun isScreenshot(item: MediaItem): Boolean {
        val path = item.relativePath.lowercase()
        val name = item.name.lowercase()
        return "screenshot" in path || name.startsWith("screenshot") || "captura" in path
    }
}
