package com.ikeralvaro.phonecleaner.feature.compress

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ikeralvaro.phonecleaner.PhoneCleanerApp
import com.ikeralvaro.phonecleaner.core.notifications.Notifications
import java.io.File

/** Comprime en segundo plano (servicio en primer plano con notificación de progreso). */
class CompressWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PhoneCleanerApp).container
        val jobFile = File(inputData.getString(KEY_JOB_FILE) ?: return Result.failure())
        val mode = runCatching { CompressionMode.valueOf(inputData.getString(KEY_MODE).orEmpty()) }
            .getOrDefault(CompressionMode.HIGH_QUALITY)
        val items = CompressJobs.read(jobFile)
        if (items.isEmpty()) return Result.success(workDataOf(KEY_SAVED to 0L, KEY_COMPRESSED to 0))

        var saved = 0L
        val compressedOriginals = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            setProgress(workDataOf(KEY_DONE to index, KEY_TOTAL to items.size, KEY_SAVED to saved))
            runCatching { setForeground(foregroundInfo(index, items.size)) }
            val outcome = if (item.isVideo) {
                container.videoCompressor.compress(item, mode) { }
            } else {
                container.photoCompressor.compress(item, mode)
            }
            if (outcome is CompressionOutcome.Saved) {
                saved += outcome.savedBytes
                compressedOriginals += item.uri
            }
        }
        val originalsFile = File(applicationContext.filesDir, CompressViewModel.ORIGINALS_FILE).apply {
            parentFile?.mkdirs()
            writeText(compressedOriginals.joinToString("\n"))
        }
        jobFile.delete()
        return Result.success(
            workDataOf(
                KEY_SAVED to saved,
                KEY_COMPRESSED to compressedOriginals.size,
                KEY_TOTAL to items.size,
                KEY_ORIGINALS_FILE to originalsFile.path,
            ),
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val notification = Notifications.compressProgress(applicationContext, done, total)
        return when {
            Build.VERSION.SDK_INT >= 35 ->
                ForegroundInfo(Notifications.ID_COMPRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= 29 ->
                ForegroundInfo(Notifications.ID_COMPRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(Notifications.ID_COMPRESS, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = "compress"
        const val KEY_JOB_FILE = "job_file"
        const val KEY_MODE = "mode"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_SAVED = "saved"
        const val KEY_COMPRESSED = "compressed"
        const val KEY_ORIGINALS_FILE = "originals_file"
    }
}
