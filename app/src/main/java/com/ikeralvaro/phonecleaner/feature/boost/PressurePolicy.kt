package com.ikeralvaro.phonecleaner.feature.boost

import kotlin.math.max

/** Por qué se dejó de llenar la memoria. */
enum class StopReason { NONE, LOW_MEMORY, MARGIN, CAP, TIMEOUT, SYSTEM_WARNING, ALLOCATION_FAILED, CANCELLED }

/** Una lectura de memoria tomada antes de reservar el siguiente bloque. */
data class PressureSample(
    val availableBytes: Long,
    val thresholdBytes: Long,
    val totalBytes: Long,
    val lowMemory: Boolean,
    val allocatedBytes: Long,
    val elapsedMs: Long,
    val systemWarning: Boolean,
)

/**
 * Freno de seguridad de "Liberar RAM" (lógica pura, probada en tests).
 *
 * Phone Cleaner llena la memoria para que el low memory killer de Android cierre las apps en segundo plano
 * (las cachés se matan mucho antes que la app en primer plano). Se para antes de que Android empiece a
 * considerar la memoria crítica, para no poner en riesgo el sistema ni a la propia app.
 */
object PressurePolicy {
    const val CHUNK_BYTES = 32L * 1024 * 1024
    const val MAX_FRACTION_OF_TOTAL = 0.75
    const val MAX_DURATION_MS = 10_000L
    const val MIN_MARGIN_BYTES = 96L * 1024 * 1024

    /** Margen sobre el umbral de "memoria baja" de Android: un cuarto del umbral, como mínimo 96 MB. */
    fun marginBytes(thresholdBytes: Long): Long = max(MIN_MARGIN_BYTES, thresholdBytes / 4)

    fun stopReason(s: PressureSample): StopReason = when {
        s.systemWarning -> StopReason.SYSTEM_WARNING
        s.lowMemory -> StopReason.LOW_MEMORY
        s.availableBytes - CHUNK_BYTES < s.thresholdBytes + marginBytes(s.thresholdBytes) -> StopReason.MARGIN
        s.allocatedBytes + CHUNK_BYTES > s.totalBytes * MAX_FRACTION_OF_TOTAL -> StopReason.CAP
        s.elapsedMs >= MAX_DURATION_MS -> StopReason.TIMEOUT
        else -> StopReason.NONE
    }
}
