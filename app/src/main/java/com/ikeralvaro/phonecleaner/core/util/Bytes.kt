package com.ikeralvaro.phonecleaner.core.util

import java.util.Locale
import kotlin.math.abs

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

/**
 * Formatea un tamaño en bytes con base 1000, igual que los Ajustes de Android desde la versión 8.
 * Ejemplos (es): 0 B, 512 B, 1,5 KB, 87 GB.
 */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    var value = abs(bytes).toDouble()
    var unit = 0
    while (unit < UNITS.lastIndex && value >= 999.5) {
        value /= 1000.0
        unit++
    }
    val number = when {
        unit == 0 -> String.format(locale, "%d", abs(bytes))
        value < 9.95 -> String.format(locale, "%.1f", value)
        else -> String.format(locale, "%.0f", value)
    }
    val sign = if (bytes < 0) "-" else ""
    return "$sign$number ${UNITS[unit]}"
}

/** Fracción segura (0..1) de [part] sobre [total]. */
fun fractionOf(part: Long, total: Long): Float =
    if (total <= 0L) 0f else (part.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
