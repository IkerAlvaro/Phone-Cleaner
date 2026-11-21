package com.ikeralvaro.phonecleaner.core.system

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.ikeralvaro.phonecleaner.core.util.fractionOf
import java.io.IOException

data class StorageSnapshot(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = fractionOf(usedBytes, totalBytes)
}

class StorageReader(private val context: Context) {
    /** Lee el almacenamiento interno. Llamar fuera del hilo principal. */
    fun read(): StorageSnapshot = if (Build.VERSION.SDK_INT < 26) statFs() else try {
        val stats = context.getSystemService(StorageStatsManager::class.java)
        StorageSnapshot(
            totalBytes = stats.getTotalBytes(StorageManager.UUID_DEFAULT),
            freeBytes = stats.getFreeBytes(StorageManager.UUID_DEFAULT),
        )
    } catch (_: IOException) {
        statFs()
    } catch (_: RuntimeException) {
        statFs()
    }

    private fun statFs(): StorageSnapshot {
        val fs = StatFs(Environment.getDataDirectory().path)
        return StorageSnapshot(fs.totalBytes, fs.availableBytes)
    }
}
