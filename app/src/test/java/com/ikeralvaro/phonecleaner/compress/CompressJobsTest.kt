package com.ikeralvaro.phonecleaner.compress

import com.ikeralvaro.phonecleaner.feature.compress.CompressJobs
import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompressJobsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun item(id: Long, name: String = "IMG_$id.jpg", path: String? = "/storage/emulated/0/DCIM/Camera/$name") =
        MediaItem(id, "content://media/external/images/media/$id", name, 1_000_000L + id, 4000, 3000, 1_700_000_000_000L,
            "image/jpeg", "DCIM/Camera/", path, durationMs = 0L)

    @Test fun roundTripKeepsEveryField() {
        val file = tmp.root.resolve("job.txt")
        val items = listOf(item(1), item(2, path = null), item(3).copy(mimeType = "video/mp4", durationMs = 12_345L))
        CompressJobs.write(file, items)
        assertEquals(items, CompressJobs.read(file))
    }

    @Test fun namesWithTabsNewlinesAndBackslashesSurvive() {
        val file = tmp.root.resolve("job.txt")
        val weird = item(7, name = "a\tb\nc\\d\r.jpg").copy(relativePath = "Mis\tfotos/\n")
        CompressJobs.write(file, listOf(weird, item(8)))
        val read = CompressJobs.read(file)
        assertEquals(listOf(weird, item(8)), read)
    }

    @Test fun removeDropsOnlyThatItem() {
        val file = tmp.root.resolve("job.txt")
        CompressJobs.write(file, listOf(item(1), item(2), item(3)))
        CompressJobs.remove(file, 2)
        assertEquals(listOf(1L, 3L), CompressJobs.read(file).map { it.id })
        CompressJobs.remove(file, 1)
        CompressJobs.remove(file, 3)
        assertTrue(CompressJobs.read(file).isEmpty())
    }

    @Test fun originalsAreAppendedPerItem() {
        val file = tmp.root.resolve("originals.txt")
        assertTrue(CompressJobs.readOriginals(file).isEmpty())
        CompressJobs.appendOriginal(file, "content://media/1", 300)
        CompressJobs.appendOriginal(file, "content://media/a\tb", 200)
        assertEquals(listOf("content://media/1" to 300L, "content://media/a\tb" to 200L), CompressJobs.readOriginals(file))
    }

    @Test fun missingFileReadsAsEmpty() {
        assertTrue(CompressJobs.read(tmp.root.resolve("nope.txt")).isEmpty())
    }
}
