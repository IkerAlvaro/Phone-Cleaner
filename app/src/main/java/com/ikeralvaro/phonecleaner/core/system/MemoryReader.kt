package com.ikeralvaro.phonecleaner.core.system

import android.app.ActivityManager
import android.content.Context
import com.ikeralvaro.phonecleaner.core.util.fractionOf

data class MemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = fractionOf(usedBytes, totalBytes)
}

class MemoryReader(private val context: Context) {
    fun read(): MemorySnapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemorySnapshot(info.totalMem, info.availMem, info.threshold, info.lowMemory)
    }
}
