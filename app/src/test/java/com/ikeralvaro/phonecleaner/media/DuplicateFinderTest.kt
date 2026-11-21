package com.ikeralvaro.phonecleaner.media

import com.ikeralvaro.phonecleaner.feature.media.DuplicateFinder
import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import com.ikeralvaro.phonecleaner.feature.media.PerceptualHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFinderTest {
    private fun item(id: Long, size: Long = 1000, w: Int = 4000, h: Int = 3000, taken: Long = id * 1000, path: String = "DCIM/Camera/", name: String = "IMG_$id.jpg") =
        MediaItem(id, "content://media/external/images/media/$id", name, size, w, h, taken, "image/jpeg", path)

    // --- dHash ---
    @Test fun gradientHashIsAllOnesOrZeros() {
        val decreasing = IntArray(72) { i -> 255 - (i % 9) * 20 }
        val increasing = IntArray(72) { i -> (i % 9) * 20 }
        assertEquals(-1L, PerceptualHash.dHash(decreasing)) // 64 bits a 1
        assertEquals(0L, PerceptualHash.dHash(increasing))
    }

    @Test fun smallChangesKeepDistanceLow() {
        val base = IntArray(72) { i -> (i * 37) % 256 }
        val tweaked = base.copyOf().also { it[10] = it[10] + 3 }
        val d = PerceptualHash.distance(PerceptualHash.dHash(base), PerceptualHash.dHash(tweaked))
        assertTrue("distancia $d", d <= 2)
    }

    @Test fun luminanceOfPureColours() {
        val luma = PerceptualHash.luminance(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF00FF00.toInt()))
        assertEquals(255, luma[0])
        assertEquals(0, luma[1])
        assertEquals(149, luma[2])
    }

    @Test fun distanceCountsDifferentBits() {
        assertEquals(0, PerceptualHash.distance(42L, 42L))
        assertEquals(64, PerceptualHash.distance(0L, -1L))
        assertEquals(2, PerceptualHash.distance(0b1010L, 0b0000L))
    }

    // --- Duplicados exactos ---
    @Test fun exactDuplicatesNeedSameSizeAndSameContent() {
        val a = item(1, size = 500)
        val b = item(2, size = 500)
        val c = item(3, size = 500)
        val d = item(4, size = 999)
        val hashes = mapOf(1L to "x", 2L to "x", 3L to "y", 4L to "x")
        val groups = DuplicateFinder.exactDuplicates(listOf(a, b, c, d)) { hashes[it.id] }
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].items.map { it.id })
    }

    @Test fun onlyHashesFilesWithRepeatedSize() {
        val hashed = mutableListOf<Long>()
        DuplicateFinder.exactDuplicates(listOf(item(1, size = 1), item(2, size = 2))) { hashed += it.id; "h" }
        assertTrue(hashed.isEmpty())
    }

    @Test fun keeperIsHighestResolutionThenNewest() {
        val low = item(1, w = 1000, h = 1000, taken = 5000)
        val high = item(2, w = 4000, h = 3000, taken = 1000)
        assertEquals(2L, DuplicateFinder.chooseKeeper(listOf(low, high)).id)
        val older = item(3, taken = 1000)
        val newer = item(4, taken = 9000)
        assertEquals(4L, DuplicateFinder.chooseKeeper(listOf(older, newer)).id)
    }

    @Test fun removableBytesExcludeKeeper() {
        val groups = DuplicateFinder.exactDuplicates(listOf(item(1, size = 300), item(2, size = 300), item(3, size = 300))) { "same" }
        assertEquals(600L, groups.single().removableBytes)
    }

    // --- Similares ---
    @Test fun similarPhotosAreGroupedTransitively() {
        val items = listOf(item(1, taken = 0), item(2, taken = 1000), item(3, taken = 2000), item(4, taken = 3000))
        val hashes = mapOf(
            1L to 0b0000L,
            2L to 0b0111L, // a 3 de la 1
            3L to 0b1111111L, // a 4 de la 2 y 7 de la 1: entra por transitividad
            4L to -1L, // muy distinta
        )
        val groups = DuplicateFinder.similarGroups(items, hashes, maxDistance = 4)
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L, 3L), groups[0].items.map { it.id }.toSet())
    }

    @Test fun similarPhotosFarApartInTimeAreNotGrouped() {
        val items = listOf(item(1, taken = 0), item(2, taken = 10 * 60 * 60 * 1000L))
        val hashes = mapOf(1L to 0L, 2L to 0L)
        assertTrue(DuplicateFinder.similarGroups(items, hashes).isEmpty())
    }

    @Test fun screenshotsAreDetected() {
        assertTrue(DuplicateFinder.isScreenshot(item(1, path = "Pictures/Screenshots/")))
        assertTrue(DuplicateFinder.isScreenshot(item(2, path = "DCIM/", name = "Screenshot_2026.png")))
        assertFalse(DuplicateFinder.isScreenshot(item(3)))
    }
}
