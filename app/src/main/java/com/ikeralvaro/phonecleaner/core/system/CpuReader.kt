package com.ikeralvaro.phonecleaner.core.system

import android.os.Build
import java.io.File

data class CoreFreq(val index: Int, val curKhz: Long?, val minKhz: Long?, val maxKhz: Long?)

data class CpuSnapshot(
    val cores: Int,
    val socName: String,
    val freqs: List<CoreFreq>,
    val estimatedLoad: Float?,
)

object CpuMath {
    fun parseKhz(text: String?): Long? = text?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    /**
     * "Actividad estimada": media de (actual - mínima) / (máxima - mínima) de los núcleos legibles.
     * Android no deja leer el uso real de CPU (/proc/stat) desde la versión 8, así que es una aproximación.
     */
    fun estimateLoad(freqs: List<CoreFreq>): Float? {
        val loads = freqs.mapNotNull { core ->
            val cur = core.curKhz ?: return@mapNotNull null
            val max = core.maxKhz ?: return@mapNotNull null
            val min = core.minKhz ?: 0L
            if (max <= min) return@mapNotNull null
            ((cur - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
        }
        return if (loads.isEmpty()) null else loads.average().toFloat()
    }
}

class CpuReader(private val cpuRoot: File = File("/sys/devices/system/cpu")) {
    private val cores = Runtime.getRuntime().availableProcessors()

    /** Mínimo y máximo de cada núcleo: son fijos, se leen una sola vez. */
    private val limits: List<Pair<Long?, Long?>> by lazy {
        (0 until cores).map { i ->
            val dir = File(cpuRoot, "cpu$i/cpufreq")
            CpuMath.parseKhz(readText(File(dir, "cpuinfo_min_freq"))) to
                CpuMath.parseKhz(readText(File(dir, "cpuinfo_max_freq")))
        }
    }

    private val soc: String by lazy { socName() }

    fun read(): CpuSnapshot {
        val freqs = (0 until cores).map { i ->
            val (min, max) = limits[i]
            CoreFreq(
                index = i,
                curKhz = CpuMath.parseKhz(readText(File(cpuRoot, "cpu$i/cpufreq/scaling_cur_freq"))),
                minKhz = min,
                maxKhz = max,
            )
        }
        return CpuSnapshot(cores, soc, freqs, CpuMath.estimateLoad(freqs))
    }

    private fun readText(file: File): String? = try {
        if (file.canRead()) file.readText() else null
    } catch (_: Exception) {
        null
    }

    private fun socName(): String {
        if (Build.VERSION.SDK_INT >= 31) {
            val model = Build.SOC_MODEL
            if (!model.isNullOrBlank() && model != Build.UNKNOWN) {
                val maker = Build.SOC_MANUFACTURER.takeUnless { it.isNullOrBlank() || it == Build.UNKNOWN }
                return listOfNotNull(maker, model).joinToString(" ")
            }
        }
        return Build.HARDWARE.orEmpty()
    }
}
