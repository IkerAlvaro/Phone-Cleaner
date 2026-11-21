package com.ikeralvaro.phonecleaner.compress

import com.ikeralvaro.phonecleaner.feature.compress.CompressionMode.HIGH_QUALITY
import com.ikeralvaro.phonecleaner.feature.compress.CompressionMode.MAX_SAVINGS
import com.ikeralvaro.phonecleaner.feature.compress.CompressionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPlannerTest {
    @Test fun maxSavingsUsesLowerQuality() {
        assertEquals(85, CompressionPlanner.photoQuality(HIGH_QUALITY))
        assertTrue(CompressionPlanner.photoQuality(MAX_SAVINGS) < 85)
    }

    @Test fun photoEstimateNeverExceedsOriginal() {
        // 12 MP a 0,22 B/px ≈ 2,6 MB
        assertEquals(2_640_000L, CompressionPlanner.estimatePhotoBytes(4000, 3000, 6_000_000, HIGH_QUALITY))
        assertEquals(1_000_000L, CompressionPlanner.estimatePhotoBytes(4000, 3000, 1_000_000, HIGH_QUALITY))
    }

    @Test fun onlyLargeEnoughJpegsAreCandidates() {
        assertTrue(CompressionPlanner.isPhotoCandidate("image/jpeg", 6_000_000, 4000, 3000, HIGH_QUALITY))
        assertFalse(CompressionPlanner.isPhotoCandidate("image/png", 6_000_000, 4000, 3000, HIGH_QUALITY))
        assertFalse(CompressionPlanner.isPhotoCandidate("image/heic", 6_000_000, 4000, 3000, HIGH_QUALITY))
        assertFalse(CompressionPlanner.isPhotoCandidate("image/jpeg", 300_000, 1000, 800, HIGH_QUALITY))
    }

    @Test fun alreadyEfficientPhotosAreSkipped() {
        // 12 MP que ya pesa 2,7 MB: la estimación (2,64 MB) no ahorra un 10 %.
        assertFalse(CompressionPlanner.isPhotoCandidate("image/jpeg", 2_700_000, 4000, 3000, HIGH_QUALITY))
    }

    @Test fun hugePhotosAreSkippedToAvoidRunningOutOfMemory() {
        assertFalse(CompressionPlanner.isPhotoCandidate("image/jpeg", 60_000_000, 12000, 9000, HIGH_QUALITY))
    }

    @Test fun bitrateFromSizeAndDuration() {
        // 60 MB en 30 s = 16 Mbps
        assertEquals(16_000_000, CompressionPlanner.bitrateFromSize(60_000_000, 30_000))
        assertEquals(0, CompressionPlanner.bitrateFromSize(1, 0))
    }

    @Test fun phone1080pVideoGetsHevcTarget() {
        val target = CompressionPlanner.videoTargetBitrate(1920, 1080, 30f, 17_000_000, hevc = true, mode = HIGH_QUALITY)
        assertEquals(5_000_000, target)
    }

    @Test fun avcNeedsMoreBitrateAndHighFpsToo() {
        val avc = CompressionPlanner.videoTargetBitrate(1920, 1080, 30f, 40_000_000, hevc = false, mode = HIGH_QUALITY)!!
        val avc60 = CompressionPlanner.videoTargetBitrate(1920, 1080, 60f, 40_000_000, hevc = false, mode = HIGH_QUALITY)!!
        assertEquals(7_500_000, avc)
        assertEquals(10_500_000, avc60)
    }

    @Test fun targetNeverGoesBelowQualityBitrateToSaveMore() {
        // 4K a 20 Mbps: se baja a 16 Mbps (ahorra ~19 %), no más.
        val target = CompressionPlanner.videoTargetBitrate(3840, 2160, 30f, 20_000_000, hevc = true, mode = HIGH_QUALITY)!!
        assertEquals(16_000_000, target)
        // 4K a 18 Mbps: no llega al 15 % de ahorro sin perder calidad.
        assertNull(CompressionPlanner.videoTargetBitrate(3840, 2160, 30f, 18_000_000, hevc = true, mode = HIGH_QUALITY))
    }

    @Test fun efficientVideosAreSkipped() {
        assertNull(CompressionPlanner.videoTargetBitrate(1280, 720, 30f, 2_000_000, hevc = true, mode = HIGH_QUALITY))
        assertNotNull(CompressionPlanner.videoTargetBitrate(1280, 720, 30f, 8_000_000, hevc = true, mode = HIGH_QUALITY))
    }

    @Test fun maxSavingsLowersVideoTarget() {
        val high = CompressionPlanner.videoTargetBitrate(1920, 1080, 30f, 30_000_000, true, HIGH_QUALITY)!!
        val max = CompressionPlanner.videoTargetBitrate(1920, 1080, 30f, 30_000_000, true, MAX_SAVINGS)!!
        assertTrue(max < high)
    }

    @Test fun videoSizeEstimateIncludesAudio() {
        // (5 Mbps + 128 kbps) × 60 s / 8 = 38,46 MB
        assertEquals(38_460_000L, CompressionPlanner.estimateVideoBytes(60_000, 5_000_000))
    }

    @Test fun worthItThreshold() {
        assertTrue(CompressionPlanner.isWorthIt(100, 90, 0.10))
        assertFalse(CompressionPlanner.isWorthIt(100, 91, 0.10))
        assertFalse(CompressionPlanner.isWorthIt(0, 0, 0.10))
    }
}
