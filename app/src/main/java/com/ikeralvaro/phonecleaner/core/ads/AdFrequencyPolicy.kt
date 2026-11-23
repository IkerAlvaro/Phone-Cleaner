package com.ikeralvaro.phonecleaner.core.ads

import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.core.util.MINUTE_MS

/** Historial guardado para decidir cuándo mostrar el anuncio a pantalla completa. */
data class AdHistory(
    val tasksCompleted: Int = 0,
    val lastShownAt: Long = 0L,
    /** Día (local) al que corresponde [shownToday]. */
    val day: Long = 0L,
    val shownToday: Int = 0,
)

/**
 * Cuándo se puede mostrar el anuncio a pantalla completa al terminar una tarea (lógica pura, con tests).
 * Pensada para que se vea sin hartar: nunca en la primera tarea, como mínimo 3 minutos entre anuncios y
 * como mucho 6 al día. Nunca si el usuario compró «Quitar anuncios» o no ha dado su consentimiento.
 */
object AdFrequencyPolicy {
    const val MIN_INTERVAL_MS = 3 * MINUTE_MS
    const val MAX_PER_DAY = 6
    const val FIRST_TASK_WITH_AD = 2

    fun dayIndex(now: Long, utcOffsetMs: Long): Long = Math.floorDiv(now + utcOffsetMs, DAY_MS)

    /** Registra que el usuario ha terminado una tarea (limpieza, liberar RAM, compresión). */
    fun afterTask(history: AdHistory): AdHistory = history.copy(tasksCompleted = history.tasksCompleted + 1)

    fun shouldShow(
        history: AdHistory,
        now: Long,
        utcOffsetMs: Long,
        premium: Boolean,
        canRequestAds: Boolean,
    ): Boolean {
        if (premium || !canRequestAds) return false
        if (history.tasksCompleted < FIRST_TASK_WITH_AD) return false
        // Si el reloj se atrasó (now < lastShownAt) no se bloquea indefinidamente: no se aplica el intervalo.
        val elapsed = now - history.lastShownAt
        if (history.lastShownAt > 0 && elapsed in 0 until MIN_INTERVAL_MS) return false
        val today = dayIndex(now, utcOffsetMs)
        val shownToday = if (history.day == today) history.shownToday else 0
        return shownToday < MAX_PER_DAY
    }

    fun afterShown(history: AdHistory, now: Long, utcOffsetMs: Long): AdHistory {
        val today = dayIndex(now, utcOffsetMs)
        val shownToday = if (history.day == today) history.shownToday else 0
        return history.copy(lastShownAt = now, day = today, shownToday = shownToday + 1)
    }
}
