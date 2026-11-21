package com.ikeralvaro.phonecleaner.core.system

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, UNKNOWN }

data class ThermalSnapshot(val level: ThermalLevel, val cpuTempC: Float?)

object ThermalMath {
    fun levelFromStatus(status: Int): ThermalLevel = when (status) {
        0 -> ThermalLevel.NONE
        1 -> ThermalLevel.LIGHT
        2 -> ThermalLevel.MODERATE
        3 -> ThermalLevel.SEVERE
        4, 5, 6 -> ThermalLevel.CRITICAL // CRITICAL, EMERGENCY, SHUTDOWN
        else -> ThermalLevel.UNKNOWN
    }

    /** Las zonas térmicas dan la temperatura en miligrados o en grados según el fabricante. */
    fun parseZoneTemp(raw: String?): Float? {
        val value = raw?.trim()?.toLongOrNull() ?: return null
        val celsius = if (value >= 1000 || value <= -1000) value / 1000f else value.toFloat()
        return celsius.takeIf { it > 5f && it < 130f }
    }

    fun isCpuZone(type: String): Boolean {
        val t = type.lowercase()
        return listOf("cpu", "tsens", "soc", "x86_pkg", "apss", "big", "little", "cluster").any { it in t } &&
            "battery" !in t && "gpu" !in t
    }
}

class ThermalReader(
    private val context: Context,
    private val thermalRoot: File = File("/sys/class/thermal"),
) {
    fun read(): ThermalSnapshot {
        val level = if (Build.VERSION.SDK_INT >= 29) {
            val pm = context.getSystemService(PowerManager::class.java)
            ThermalMath.levelFromStatus(pm.currentThermalStatus)
        } else {
            ThermalLevel.UNKNOWN
        }
        return ThermalSnapshot(level, readCpuTemp())
    }

    /** Temperatura máxima de las zonas de CPU legibles; muchos móviles las bloquean y devuelve null. */
    private fun readCpuTemp(): Float? = try {
        thermalRoot.listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.mapNotNull { zone ->
                val type = File(zone, "type").takeIf { it.canRead() }?.readText() ?: return@mapNotNull null
                if (!ThermalMath.isCpuZone(type)) return@mapNotNull null
                ThermalMath.parseZoneTemp(File(zone, "temp").takeIf { it.canRead() }?.readText())
            }
            ?.maxOrNull()
    } catch (_: Exception) {
        null
    }
}
