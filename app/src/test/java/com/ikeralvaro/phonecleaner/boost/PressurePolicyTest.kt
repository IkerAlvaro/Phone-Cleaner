package com.ikeralvaro.phonecleaner.boost

import com.ikeralvaro.phonecleaner.feature.boost.NoiseFiller
import com.ikeralvaro.phonecleaner.feature.boost.PressurePolicy
import com.ikeralvaro.phonecleaner.feature.boost.PressureSample
import com.ikeralvaro.phonecleaner.feature.boost.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.Deflater

class PressurePolicyTest {
    private val mb = 1024L * 1024
    private val gb = 1024 * mb

    private fun sample(
        avail: Long = 3 * gb,
        threshold: Long = 256 * mb,
        total: Long = 8 * gb,
        low: Boolean = false,
        allocated: Long = 0,
        elapsed: Long = 0,
        warning: Boolean = false,
    ) = PressureSample(avail, threshold, total, low, allocated, elapsed, warning)

    @Test fun keepsGoingWhileThereIsRoom() {
        assertEquals(StopReason.NONE, PressurePolicy.stopReason(sample()))
    }

    @Test fun stopsWhenAndroidReportsLowMemory() {
        assertEquals(StopReason.LOW_MEMORY, PressurePolicy.stopReason(sample(low = true)))
    }

    @Test fun stopsBeforeTheNextChunkWouldCrossTheSafetyMargin() {
        // umbral 256 MB + margen 96 MB = 352 MB; con 380 MB libres, un bloque de 32 MB lo cruzaría.
        assertEquals(StopReason.MARGIN, PressurePolicy.stopReason(sample(avail = 380 * mb)))
        assertEquals(StopReason.NONE, PressurePolicy.stopReason(sample(avail = 400 * mb)))
    }

    @Test fun marginGrowsWithBigThresholds() {
        assertEquals(96 * mb, PressurePolicy.marginBytes(200 * mb))
        assertEquals(200 * mb, PressurePolicy.marginBytes(800 * mb))
    }

    @Test fun neverAllocatesMoreThanThreeQuartersOfTheRam() {
        assertEquals(StopReason.CAP, PressurePolicy.stopReason(sample(allocated = 6 * gb - 16 * mb, avail = 6 * gb)))
    }

    @Test fun stopsAfterTenSeconds() {
        assertEquals(StopReason.TIMEOUT, PressurePolicy.stopReason(sample(elapsed = 10_000)))
    }

    @Test fun systemWarningWinsOverEverything() {
        assertEquals(StopReason.SYSTEM_WARNING, PressurePolicy.stopReason(sample(warning = true, low = true)))
    }

    @Test fun noiseIsDeterministicAndIncompressible() {
        val a = NoiseFiller.pattern(1024 * 1024, seed = 42)
        val b = NoiseFiller.pattern(1024 * 1024, seed = 42)
        assertTrue(a.contentEquals(b))
        assertTrue(a.toSet().size > 250)

        // zram usa compresores rápidos (lz4/zstd): si deflate no lo reduce, ellos tampoco.
        val deflater = Deflater(Deflater.BEST_SPEED).apply { setInput(a); finish() }
        val buffer = ByteArray(a.size + 1024)
        val compressed = deflater.deflate(buffer)
        assertTrue("comprimido a $compressed de ${a.size}", compressed > a.size * 0.98)
    }

    @Test fun noPageRepeatsInsideThePattern() {
        val pattern = NoiseFiller.pattern(1024 * 1024, seed = 7)
        val pages = pattern.toList().chunked(4096).map { it.hashCode() }
        assertEquals(pages.size, pages.toSet().size)
    }

    @Test fun zeroSeedStillProducesNoise() {
        val zero = NoiseFiller.pattern(4096, seed = 0)
        assertTrue(zero.any { it != 0.toByte() })
    }
}
