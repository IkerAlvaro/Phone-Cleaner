package com.ikeralvaro.phonecleaner.feature.media

/** Agrupación pura de duplicados exactos y fotos similares. */
object DuplicateFinder {
    const val SIMILAR_MAX_DISTANCE = 6
    const val SIMILAR_MAX_TIME_GAP_MS = 2 * 60 * 60 * 1000L

    /** Similares: se conserva la de mayor resolución; si empatan, la más reciente; si no, la de menor id. */
    fun chooseKeeper(items: List<MediaItem>): MediaItem = items.sortedWith(
        compareByDescending<MediaItem> { it.pixels }
            .thenByDescending { it.dateTakenMs }
            .thenBy { it.id },
    ).first()

    /**
     * Duplicados exactos (mismo contenido): se conserva la de la carpeta de la cámara (DCIM/Camera); si no, la
     * más antigua (la original, las demás suelen ser copias); si empatan, la de menor id.
     */
    fun chooseExactKeeper(items: List<MediaItem>): MediaItem = items.sortedWith(
        compareByDescending<MediaItem> { isCameraFolder(it) }
            .thenBy { if (it.dateTakenMs > 0) it.dateTakenMs else Long.MAX_VALUE }
            .thenBy { it.id },
    ).first()

    fun isCameraFolder(item: MediaItem): Boolean {
        val folder = (item.relativePath.ifBlank { item.path?.substringBeforeLast('/', "").orEmpty() })
            .replace('\\', '/').trim('/').lowercase()
        return folder == "dcim/camera" || folder.endsWith("/dcim/camera")
    }

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
            .map { group -> MediaGroup(group.sortedBy { it.id }, chooseExactKeeper(group).id) }
            .sortedByDescending { it.removableBytes }

    /**
     * Fotos similares: agrupación voraz por fecha, SIN transitividad. Cada grupo tiene una foto ancla (la más
     * antigua aún sin grupo) y solo entran las fotos tomadas a menos de [maxTimeGapMs] del ancla cuyo hash
     * perceptual está a distancia ≤ [maxDistance] del ancla. Así una cadena A~B~C no junta A con C si no se
     * parecen entre sí.
     */
    fun similarGroups(
        items: List<MediaItem>,
        hashes: Map<Long, Long>,
        maxDistance: Int = SIMILAR_MAX_DISTANCE,
        maxTimeGapMs: Long = SIMILAR_MAX_TIME_GAP_MS,
    ): List<MediaGroup> {
        val hashed = items.filter { it.id in hashes }.sortedWith(compareBy<MediaItem> { it.dateTakenMs }.thenBy { it.id })
        val assigned = BooleanArray(hashed.size)
        val groups = mutableListOf<MediaGroup>()
        for (i in hashed.indices) {
            if (assigned[i]) continue
            val anchor = hashed[i]
            val anchorHash = hashes.getValue(anchor.id)
            val members = mutableListOf(anchor)
            var j = i + 1
            // Ordenadas por fecha: en cuanto se pasa la ventana de tiempo del ancla, se deja de comparar.
            while (j < hashed.size && hashed[j].dateTakenMs - anchor.dateTakenMs <= maxTimeGapMs) {
                if (!assigned[j] && PerceptualHash.distance(anchorHash, hashes.getValue(hashed[j].id)) <= maxDistance) {
                    assigned[j] = true
                    members += hashed[j]
                }
                j++
            }
            assigned[i] = true
            if (members.size > 1) groups += MediaGroup(members, chooseKeeper(members).id)
        }
        return groups.sortedByDescending { it.removableBytes }
    }

    fun isScreenshot(item: MediaItem): Boolean {
        val path = item.relativePath.lowercase()
        val name = item.name.lowercase()
        return "screenshot" in path || name.startsWith("screenshot") || "captura" in path
    }
}
