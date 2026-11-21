package com.ikeralvaro.phonecleaner.feature.boost

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.SharedMemory
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import com.ikeralvaro.phonecleaner.core.system.MemoryReader
import com.ikeralvaro.phonecleaner.feature.apps.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

enum class BoostStage { FILLING, HOLDING, RELEASING, MEASURING }

data class BoostProgress(val stage: BoostStage, val usedFraction: Float, val allocatedBytes: Long)

data class BoostResult(val freedBytes: Long, val stopReason: StopReason, val peakAllocatedBytes: Long)

/**
 * Libera RAM llenando la memoria unos segundos: así el low memory killer de Android cierra por su cuenta las
 * apps en segundo plano (nunca la app en primer plano, que es Phone Cleaner). Después se suelta todo y se
 * mide cuánta memoria ha quedado libre de verdad.
 *
 * La memoria se reserva fuera del heap de Java (SharedMemory o píxeles de Bitmap), para que el límite de
 * memoria de la app no la corte, y se rellena con ruido para que la compresión zram no la anule.
 * [PressurePolicy] decide cuándo parar antes de que la memoria llegue a niveles críticos.
 */
class MemoryPressureBooster(
    private val context: Context,
    private val memory: MemoryReader,
    private val apps: AppRepository,
) {
    suspend fun boost(onProgress: (BoostProgress) -> Unit): BoostResult = withContext(Dispatchers.Default) {
        val before = memory.read()
        if (Build.VERSION.SDK_INT < 26) return@withContext legacyBoost(before, onProgress)
        val warning = AtomicBoolean(false)
        val callbacks = object : ComponentCallbacks2 {
            // RUNNING_LOW/CRITICAL: el sistema va justo. UI_HIDDEN o más: el usuario ha salido de la app.
            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) warning.set(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = warning.set(true)

            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }
        context.registerComponentCallbacks(callbacks)

        val blocks = ArrayList<MemoryBlock>()
        var allocated = 0L
        var reason = StopReason.NONE
        val pattern = NoiseFiller.pattern(PATTERN_BYTES, System.nanoTime())
        val start = SystemClock.elapsedRealtime()
        try {
            while (true) {
                ensureActive()
                val m = memory.read()
                onProgress(BoostProgress(BoostStage.FILLING, m.usedFraction, allocated))
                reason = PressurePolicy.stopReason(
                    PressureSample(
                        availableBytes = m.availableBytes,
                        thresholdBytes = m.thresholdBytes,
                        totalBytes = m.totalBytes,
                        lowMemory = m.lowMemory,
                        allocatedBytes = allocated,
                        elapsedMs = SystemClock.elapsedRealtime() - start,
                        systemWarning = warning.get(),
                    ),
                )
                if (reason != StopReason.NONE) break
                val block = try {
                    MemoryBlock.allocate(PressurePolicy.CHUNK_BYTES.toInt(), pattern)
                } catch (_: Throwable) {
                    reason = StopReason.ALLOCATION_FAILED
                    break
                }
                blocks += block
                allocated += PressurePolicy.CHUNK_BYTES
                // Deja tiempo al low memory killer para reaccionar entre bloque y bloque.
                delay(STEP_DELAY_MS)
            }
            // Se mantiene la presión un momento para que Android termine de cerrar apps.
            repeat(HOLD_STEPS) {
                ensureActive()
                if (warning.get()) return@repeat
                delay(HOLD_STEP_MS)
                onProgress(BoostProgress(BoostStage.HOLDING, memory.read().usedFraction, allocated))
            }
        } catch (e: CancellationException) {
            reason = StopReason.CANCELLED
            throw e
        } finally {
            blocks.forEach { it.release() }
            blocks.clear()
            context.unregisterComponentCallbacks(callbacks)
        }

        onProgress(BoostProgress(BoostStage.RELEASING, memory.read().usedFraction, 0L))
        // Hasta Android 13 además se pide al sistema que detenga los procesos en segundo plano.
        if (Build.VERSION.SDK_INT < 34) stopBackgroundProcesses()
        repeat(MEASURE_STEPS) {
            delay(MEASURE_STEP_MS)
            onProgress(BoostProgress(BoostStage.MEASURING, memory.read().usedFraction, 0L))
        }
        val after = memory.read()
        BoostResult((after.availableBytes - before.availableBytes).coerceAtLeast(0L), reason, allocated)
    }

    /**
     * Android 7: los píxeles de un Bitmap aún van al heap de Java (no se puede llenar la memoria del sistema),
     * pero killBackgroundProcesses sí cierra las apps en segundo plano de cualquier app.
     */
    private suspend fun legacyBoost(
        before: com.ikeralvaro.phonecleaner.core.system.MemorySnapshot,
        onProgress: (BoostProgress) -> Unit,
    ): BoostResult {
        onProgress(BoostProgress(BoostStage.HOLDING, before.usedFraction, 0L))
        stopBackgroundProcesses()
        repeat(MEASURE_STEPS) {
            delay(MEASURE_STEP_MS)
            onProgress(BoostProgress(BoostStage.MEASURING, memory.read().usedFraction, 0L))
        }
        val after = memory.read()
        return BoostResult((after.availableBytes - before.availableBytes).coerceAtLeast(0L), StopReason.NONE, 0L)
    }

    private suspend fun stopBackgroundProcesses() {
        val am = context.getSystemService(ActivityManager::class.java)
        val excluded = apps.protectedPackages()
        apps.launchablePackageNames().filter { it !in excluded }.forEach { pkg ->
            try {
                @Suppress("DEPRECATION")
                am.killBackgroundProcesses(pkg)
            } catch (_: SecurityException) {
            }
        }
    }

    private companion object {
        const val PATTERN_BYTES = 1024 * 1024
        const val STEP_DELAY_MS = 25L
        const val HOLD_STEPS = 10
        const val HOLD_STEP_MS = 100L
        const val MEASURE_STEPS = 4
        const val MEASURE_STEP_MS = 500L
    }
}

/** Bloque de memoria nativa reservado y rellenado con ruido. */
private sealed interface MemoryBlock {
    fun release()

    @RequiresApi(27)
    class Shared(private val shared: SharedMemory, private val buffer: ByteBuffer) : MemoryBlock {
        override fun release() {
            SharedMemory.unmap(buffer)
            shared.close()
        }
    }

    class Pixels(private val bitmap: Bitmap) : MemoryBlock {
        override fun release() = bitmap.recycle()
    }

    companion object {
        fun allocate(bytes: Int, pattern: ByteArray): MemoryBlock =
            if (Build.VERSION.SDK_INT >= 27) shared(bytes, pattern) else pixels(bytes, pattern)

        @RequiresApi(27)
        private fun shared(bytes: Int, pattern: ByteArray): MemoryBlock {
            val shared = SharedMemory.create("phonecleaner-boost", bytes)
            val buffer = try {
                shared.mapReadWrite()
            } catch (e: Throwable) {
                shared.close()
                throw e
            }
            // Escribir cada página la reserva de verdad (si no, el kernel no la asigna).
            while (buffer.remaining() > 0) buffer.put(pattern, 0, minOf(pattern.size, buffer.remaining()))
            return Shared(shared, buffer)
        }

        /** Android 8.0: los píxeles de un Bitmap viven en memoria nativa. */
        private fun pixels(bytes: Int, pattern: ByteArray): MemoryBlock {
            val width = 2048
            val height = bytes / 4 / width
            val bitmap = createBitmap(width, height)
            val ints = IntArray(pattern.size / 4)
            ByteBuffer.wrap(pattern).order(ByteOrder.nativeOrder()).asIntBuffer().get(ints)
            val rows = ints.size / width
            var y = 0
            while (y < height) {
                val h = minOf(rows, height - y)
                bitmap.setPixels(ints, 0, width, 0, y, width, h)
                y += h
            }
            return Pixels(bitmap)
        }
    }
}
