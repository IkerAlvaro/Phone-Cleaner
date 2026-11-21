package com.ikeralvaro.phonecleaner.apps

import com.ikeralvaro.phonecleaner.core.util.DAY_MS
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
        label: String = pkg.uppercase(),
        size: Long = 100,
        installedDaysAgo: Int = 100,
        boot: Boolean = false,
        system: Boolean = false,
        unrestricted: Boolean = false,
    ) = AppEntry(pkg, label, system, "1.0", now - installedDaysAgo * DAY_MS, now - DAY_MS, size, boot, unrestricted)

    @Test fun sortBySizeThenName() {
        val apps = listOf(app("small", size = 1), app("big", size = 900), app("mid", size = 50))
        assertEquals(listOf("big", "mid", "small"), AppsLogic.sort(apps, AppSort.SIZE).map { it.packageName })
    }

    @Test fun sortByNameIgnoresCase() {
        val apps = listOf(app("b", label = "beta"), app("a", label = "Alfa"), app("c", label = "Gamma"))
        assertEquals(listOf("a", "b", "c"), AppsLogic.sort(apps, AppSort.NAME).map { it.packageName })
    }

    @Test fun newestFirst() {
        val apps = listOf(app("old", installedDaysAgo = 300), app("new", installedDaysAgo = 1), app("mid", installedDaysAgo = 30))
        assertEquals(listOf("new", "mid", "old"), AppsLogic.sort(apps, AppSort.NEWEST).map { it.packageName })
    }

    @Test fun autostartOnlyListsUserAppsThatStartAtBoot() {
        val apps = listOf(
            app("plain"),
            app("boot", label = "Zeta", boot = true),
            app("system", boot = true, system = true),
            app("unrestricted", label = "Alfa", boot = true, unrestricted = true),
            app("boot2", label = "Beta", boot = true),
        )
        // Primero las que además no tienen restricción de batería.
        assertEquals(listOf("unrestricted", "boot2", "boot"), AppsLogic.sort(apps, AppSort.AUTOSTART).map { it.packageName })
    }

    @Test fun apkSizeAddsBaseAndSplitsOnce() {
        val sizes = mapOf("/base.apk" to 100L, "/split_a.apk" to 20L, "/split_b.apk" to 5L)
        val total = AppsLogic.apkSize(listOf("/base.apk", "/split_a.apk", null, "/split_b.apk", "/base.apk")) { sizes.getValue(it) }
        assertEquals(125L, total)
    }

    @Test fun intentQueueWalksItemsOnce() {
        val queue = IntentQueue<String>()
        assertEquals("a", queue.start(listOf("a", "b")))
        assertTrue(queue.isRunning)
        assertEquals("b", queue.next())
        assertNull(queue.next())
        assertFalse(queue.isRunning)
    }
}
