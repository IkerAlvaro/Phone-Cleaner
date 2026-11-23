package com.ikeralvaro.phonecleaner.media

import com.ikeralvaro.phonecleaner.feature.media.MediaCategory
import com.ikeralvaro.phonecleaner.feature.media.MediaGroup
import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import com.ikeralvaro.phonecleaner.feature.media.MediaUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUiStateTest {
    private fun item(id: Long, w: Int = 4000, taken: Long = id * 1000, path: String = "DCIM/Camera/") =
        MediaItem(id, "content://media/external/images/media/$id", "IMG_$id.jpg", 1000, w, 3000, taken, "image/jpeg", path)

    private val state = MediaUiState(
        duplicates = listOf(MediaGroup(listOf(item(1), item(2), item(3)), keeperId = 1)),
        similar = listOf(MediaGroup(listOf(item(10), item(11)), keeperId = 10)),
        screenshots = listOf(item(20)),
        largeVideos = listOf(item(30)),
    )

    @Test fun onlyExactDuplicatesArePreselected() {
        assertEquals(setOf(2L, 3L), state.defaultSelection(MediaCategory.DUPLICATES))
        assertTrue(state.defaultSelection(MediaCategory.SIMILAR).isEmpty())
        assertTrue(state.defaultSelection(MediaCategory.SCREENSHOTS).isEmpty())
        assertTrue(state.defaultSelection(MediaCategory.LARGE_VIDEOS).isEmpty())
    }

    @Test fun pruneRemovesItemsAndDropsGroupsWithOnePhotoLeft() {
        val pruned = state.prune(setOf(2L, 11L, 20L))
        assertEquals(listOf(1L, 3L), pruned.duplicates.single().items.map { it.id })
        assertTrue(pruned.similar.isEmpty())
        assertTrue(pruned.screenshots.isEmpty())
        assertEquals(1, pruned.largeVideos.size)
    }

    @Test fun pruneChoosesANewKeeperWithTheRightRule() {
        // Exactos: si se borra la que se conservaba, la nueva es la de la cámara aunque sea más reciente.
        val dup = MediaUiState(
            duplicates = listOf(
                MediaGroup(listOf(item(1, taken = 1), item(2, taken = 2, path = "Pictures/Copias/"), item(3, taken = 9)), keeperId = 1),
            ),
            similar = listOf(MediaGroup(listOf(item(10, w = 4000), item(11, w = 1000), item(12, w = 2000)), keeperId = 10)),
        )
        val pruned = dup.prune(setOf(1L, 10L))
        assertEquals(3L, pruned.duplicates.single().keeperId)
        // Similares: la de mayor resolución.
        assertEquals(12L, pruned.similar.single().keeperId)
    }
}
