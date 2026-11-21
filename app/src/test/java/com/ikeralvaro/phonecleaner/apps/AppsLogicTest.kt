package com.ikeralvaro.phonecleaner.apps

import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.core.util.HOUR_MS
import com.ikeralvaro.phonecleaner.core.util.IntentQueue
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import com.ikeralvaro.phonecleaner.feature.apps.AppSort
import com.ikeralvaro.phonecleaner.feature.apps.AppsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsLogicTest {
    private val now = 1_800_000_000_000L

    private fun app(
        pkg: String,
        size: Long? = 100,
        cache: Long? = 10,
        lastUsed: Long? = now - HOUR_MS,
        installed: Long = now - 365 * DAY_MS,
        fg: Long = 0,
    ) = AppEntry(pkg, pkg.uppercase(), false, "1.0", installed, size, 0, cache, lastUsed, fg, false)

    @Test fun unusedNeedsThirtyDaysWithoutUse() {
        assertTrue(AppsLogic.isUnused(app("a", lastUsed = now - 40 * DAY_MS), now))
        assertTrue(AppsLogic.isUnused(app("a", lastUsed = null), now))
        assertFalse(AppsLogic.isUnused(app("a", lastUsed = now - 2 * DAY_MS), now))
    }

    @Test fun recentlyInstalledAppsAreNeverUnused() {
        assertFalse(AppsLogic.isUnused(app("a", lastUsed = null, installed = now - 5 * DAY_MS), now))
    }

    @Test fun sortBySizeAndCache() {
        val apps = listOf(app("small", size = 1, cache = 50), app("big", size = 900, cache = 1), app("unknown", size = null, cache = null))
        assertEquals(listOf("big", "small", "unknown"), AppsLogic.sort(apps, AppSort.SIZE, now).map { it.packageName })
        assertEquals(listOf("small", "big", "unknown"), AppsLogic.sort(apps, AppSort.CACHE, now).map { it.packageName })
    }

    @Test fun unusedSortOnlyShowsUnusedApps() {
        val apps = listOf(app("used"), app("old", lastUsed = now - 60 * DAY_MS), app("never", lastUsed = null))
        assertEquals(listOf("never", "old"), AppsLogic.sort(apps, AppSort.UNUSED, now).map { it.packageName })
    }

    @Test fun boostCandidatesAreRecentAndNotProtected() {
        val apps = listOf(
            app("recent", lastUsed = now - HOUR_MS),
            app("older", lastUsed = now - 5 * HOUR_MS),
            app("launcher", lastUsed = now - 1000),
            app("newest", lastUsed = now - 60_000),
        )
        val result = AppsLogic.boostCandidates(apps, now, 3 * HOUR_MS, setOf("launcher"))
        assertEquals(listOf("newest", "recent"), result.map { it.packageName })
    }

    @Test fun intentQueueWalksItemsOnce() {
        val queue = IntentQueue<String>()
        assertEquals("a", queue.start(listOf("a", "b")))
        assertTrue(queue.isRunning)
        assertEquals(1, queue.position)
        assertEquals("b", queue.next())
        assertEquals(2, queue.position)
        assertNull(queue.next())
        assertFalse(queue.isRunning)
        assertNull(queue.next())
    }

    @Test fun emptyQueueDoesNothing() {
        val queue = IntentQueue<String>()
        assertNull(queue.start(emptyList()))
        assertFalse(queue.isRunning)
    }
}
