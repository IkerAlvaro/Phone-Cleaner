package com.ikeralvaro.phonecleaner.ads

import com.ikeralvaro.phonecleaner.core.ads.AdFrequencyPolicy
import com.ikeralvaro.phonecleaner.core.ads.AdHistory
import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.core.util.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFrequencyPolicyTest {
    private val now = 1_800_000_000_000L
    private val offset = 3_600_000L // UTC+1 (Madrid en invierno)

    private fun shows(history: AdHistory, premium: Boolean = false, consent: Boolean = true, at: Long = now) =
        AdFrequencyPolicy.shouldShow(history, at, offset, premium, consent)

    @Test fun neverOnTheFirstFinishedTask() {
        val afterFirst = AdFrequencyPolicy.afterTask(AdHistory())
        assertFalse(shows(afterFirst))
        val afterSecond = AdFrequencyPolicy.afterTask(afterFirst)
        assertTrue(shows(afterSecond))
    }

    @Test fun waitsThreeMinutesBetweenAds() {
        val shown = AdFrequencyPolicy.afterShown(AdHistory(tasksCompleted = 5), now, offset)
        assertFalse(shows(shown, at = now + 2 * MINUTE_MS))
        assertTrue(shows(shown, at = now + 3 * MINUTE_MS))
    }

    @Test fun atMostSixPerDayAndResetsNextDay() {
        var history = AdHistory(tasksCompleted = 10)
        var t = now
        repeat(AdFrequencyPolicy.MAX_PER_DAY) {
            assertTrue(shows(history, at = t))
            history = AdFrequencyPolicy.afterShown(history, t, offset)
            t += 4 * MINUTE_MS
        }
        assertEquals(6, history.shownToday)
        assertFalse(shows(history, at = t))
        assertTrue(shows(history, at = t + DAY_MS))
    }

    @Test fun neverWithRemoveAdsOrWithoutConsent() {
        val history = AdHistory(tasksCompleted = 10)
        assertFalse(shows(history, premium = true))
        assertFalse(shows(history, consent = false))
    }

    @Test fun dayIndexUsesLocalTime() {
        // 23:30 UTC del día D es 00:30 del día D+1 en UTC+1.
        val lateUtc = 20_000 * DAY_MS + 23 * 60 * MINUTE_MS + 30 * MINUTE_MS
        assertEquals(20_001L, AdFrequencyPolicy.dayIndex(lateUtc, offset))
        assertEquals(20_000L, AdFrequencyPolicy.dayIndex(lateUtc, 0))
    }

    @Test fun clockGoingBackwardsDoesNotBlockAdsForever() {
        val shown = AdFrequencyPolicy.afterShown(AdHistory(tasksCompleted = 5), now, offset)
        // El usuario atrasa el reloj una hora: sin este caso, now - lastShownAt sería negativo para siempre.
        assertTrue(shows(shown, at = now - 60 * MINUTE_MS))
    }

    @Test fun clockGoingBackwardsKeepsTheDailyCapForTheSameDay() {
        var history = AdHistory(tasksCompleted = 10)
        var t = now
        repeat(AdFrequencyPolicy.MAX_PER_DAY) {
            history = AdFrequencyPolicy.afterShown(history, t, offset)
            t += 4 * MINUTE_MS
        }
        assertFalse(shows(history, at = now + MINUTE_MS))
    }

    @Test fun afterShownOnAnEarlierDayStartsANewCount() {
        val history = AdHistory(tasksCompleted = 10, lastShownAt = now, day = AdFrequencyPolicy.dayIndex(now, offset), shownToday = 6)
        val earlier = now - 2 * DAY_MS
        assertTrue(shows(history, at = earlier))
        assertEquals(1, AdFrequencyPolicy.afterShown(history, earlier, offset).shownToday)
    }

    @Test fun zeroLastShownNeverBlocks() {
        assertTrue(shows(AdHistory(tasksCompleted = 3, lastShownAt = 0L), at = 1L))
    }
}
