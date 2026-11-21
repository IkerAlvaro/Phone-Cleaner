package com.ikeralvaro.phonecleaner.feature.boost

/**
 * Datos pseudoaleatorios para rellenar la memoria reservada.
 * Los móviles comprimen la RAM (zram): si se escribieran ceros, se comprimirían a casi nada y Android no
 * notaría la presión. Con ruido, cada página ocupa su tamaño real.
 */
object NoiseFiller {
    /** Patrón de [sizeBytes] bytes generado con xorshift64*. */
    fun pattern(sizeBytes: Int, seed: Long): ByteArray {
        val out = ByteArray(sizeBytes)
        var x = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed
        var i = 0
        while (i < sizeBytes) {
            x = x xor (x ushr 12)
            x = x xor (x shl 25)
            x = x xor (x ushr 27)
            var v = x * 0x2545F4914F6CDD1DL
            var b = 0
            while (b < 8 && i < sizeBytes) {
                out[i++] = v.toByte()
                v = v ushr 8
                b++
            }
        }
        return out
    }
}
