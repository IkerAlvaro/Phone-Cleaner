package com.ikeralvaro.phonecleaner.core

import com.ikeralvaro.phonecleaner.core.util.formatBytes
import com.ikeralvaro.phonecleaner.core.util.formatDuration
import com.ikeralvaro.phonecleaner.core.util.fractionOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class BytesTest {
    private val es = Locale.forLanguageTag("es-ES")
    private val en = Locale.US

    @Test fun bytesBelowOneKilobyte() {
        assertEquals("0 B", formatBytes(0, es))
        assertEquals("999 B", formatBytes(999, es))
    }

    @Test fun usesDecimalCommaInSpanish() {
        assertEquals("1,5 KB", formatBytes(1_500, es))
        assertEquals("1.5 KB", formatBytes(1_500, en))
    }

    @Test fun dropsDecimalsFromTenUpwards() {
        assertEquals("87 GB", formatBytes(87_000_000_000, es))
        assertEquals("128 GB", formatBytes(128_000_000_000, es))
    }

    @Test fun roundsUpToNextUnitInsteadOfShowing1000() {
        assertEquals("1,0 MB", formatBytes(999_600, es))
        assertEquals("1,0 GB", formatBytes(999_700_000, es))
    }

    @Test fun negativeValues() {
        assertEquals("-2,0 MB", formatBytes(-2_000_000, es))
    }

    @Test fun fractionIsClampedAndSafe() {
        assertEquals(0f, fractionOf(5, 0))
        assertEquals(0.5f, fractionOf(50, 100))
        assertEquals(1f, fractionOf(200, 100))
    }

    @Test fun durations() {
        assertEquals("30 s", formatDuration(30_000))
        assertEquals("45 min", formatDuration(45 * 60_000L))
        assertEquals("2 h 5 min", formatDuration((2 * 60 + 5) * 60_000L))
        assertEquals("3 h", formatDuration(3 * 3_600_000L))
        assertEquals("1 d 2 h", formatDuration(26 * 3_600_000L))
    }
}
