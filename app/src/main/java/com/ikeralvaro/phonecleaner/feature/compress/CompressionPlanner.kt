package com.ikeralvaro.phonecleaner.feature.compress

import kotlin.math.min
import kotlin.math.roundToLong

enum class CompressionMode { HIGH_QUALITY, MAX_SAVINGS }

/**
 * Decisiones puras de compresión. "Alta calidad" está pensada para que no haya diferencia visible:
 * JPEG al 85 % y vídeo HEVC con un bitrate generoso para su resolución.
 */
object CompressionPlanner {
    const val PHOTO_MIN_SAVINGS = 0.10
    const val VIDEO_MIN_SAVINGS = 0.15
    const val MIN_PHOTO_BYTES = 500_000L
    const val MIN_VIDEO_BYTES = 20_000_000L
    const val AUDIO_BITRATE = 128_000
    const val MAX_PHOTO_PIXELS = 64_000_000L

    fun photoQuality(mode: CompressionMode): Int = when (mode) {
        CompressionMode.HIGH_QUALITY -> 85
        CompressionMode.MAX_SAVINGS -> 72
    }

    /** Bytes por píxel típicos de un JPEG a esa calidad (fotos de móvil). */
    private fun bytesPerPixel(mode: CompressionMode): Double = when (mode) {
        CompressionMode.HIGH_QUALITY -> 0.22
        CompressionMode.MAX_SAVINGS -> 0.14
    }

    fun estimatePhotoBytes(width: Int, height: Int, originalBytes: Long, mode: CompressionMode): Long {
        val pixels = width.toLong() * height.toLong()
        val estimate = if (pixels > 0) (pixels * bytesPerPixel(mode)).roundToLong() else (originalBytes * 0.6).roundToLong()
        return min(estimate, originalBytes)
    }

    fun isPhotoCandidate(mimeType: String, sizeBytes: Long, width: Int, height: Int, mode: CompressionMode): Boolean {
        if (mimeType != "image/jpeg" || sizeBytes < MIN_PHOTO_BYTES) return false
        if (width.toLong() * height.toLong() > MAX_PHOTO_PIXELS) return false
        return isWorthIt(sizeBytes, estimatePhotoBytes(width, height, sizeBytes, mode), PHOTO_MIN_SAVINGS)
    }

    /** Bitrate medio de un archivo a partir de su tamaño y duración. */
    fun bitrateFromSize(sizeBytes: Long, durationMs: Long): Int =
        if (durationMs <= 0) 0 else (sizeBytes * 8_000 / durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * Bitrate de vídeo objetivo, o null si no compensa. El objetivo es el bitrate que mantiene la calidad
     * para esa resolución (base HEVC a 30 fps según el lado corto; H.264 necesita ~1,5× más). Nunca se baja
     * de ahí para ahorrar más: si el original ya está cerca de ese bitrate, se deja como está.
     */
    fun videoTargetBitrate(
        width: Int,
        height: Int,
        fps: Float,
        originalBitrate: Int,
        hevc: Boolean,
        mode: CompressionMode,
    ): Int? {
        val shortSide = min(width, height).takeIf { it > 0 } ?: 1080
        var target = when {
            shortSide <= 480 -> 1_200_000.0
            shortSide <= 720 -> 2_500_000.0
            shortSide <= 1080 -> 5_000_000.0
            shortSide <= 1440 -> 9_000_000.0
            else -> 16_000_000.0
        }
        if (!hevc) target *= 1.5
        if (fps > 40f) target *= 1.4
        if (mode == CompressionMode.MAX_SAVINGS) target *= 0.7
        if (originalBitrate > 0 && target + AUDIO_BITRATE > originalBitrate * (1 - VIDEO_MIN_SAVINGS)) return null
        return target.toInt()
    }

    fun estimateVideoBytes(durationMs: Long, videoBitrate: Int): Long =
        (videoBitrate.toLong() + AUDIO_BITRATE) * durationMs / 8_000

    fun isWorthIt(originalBytes: Long, newBytes: Long, minSavings: Double): Boolean =
        originalBytes > 0 && newBytes > 0 && newBytes <= originalBytes * (1 - minSavings)
}
