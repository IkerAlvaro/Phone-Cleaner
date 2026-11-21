package com.ikeralvaro.phonecleaner.feature.media

/**
 * dHash (hash de diferencias) de 64 bits: se reduce la imagen a 9×8 píxeles en gris y cada bit indica si un
 * píxel es más claro que su vecino de la derecha. Fotos casi iguales dan hashes con pocos bits distintos.
 */
object PerceptualHash {
    const val WIDTH = 9
    const val HEIGHT = 8

    /** Luminancia (0–255) de píxeles ARGB. */
    fun luminance(argb: IntArray): IntArray = IntArray(argb.size) { i ->
        val p = argb[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        (r * 299 + g * 587 + b * 114) / 1000
    }

    fun dHash(luma: IntArray): Long {
        require(luma.size == WIDTH * HEIGHT) { "Se esperaban ${WIDTH * HEIGHT} valores" }
        var hash = 0L
        var bit = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH - 1) {
                if (luma[y * WIDTH + x] > luma[y * WIDTH + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
