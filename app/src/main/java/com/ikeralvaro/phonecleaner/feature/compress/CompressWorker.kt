package com.ikeralvaro.phonecleaner.feature.compress

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ikeralvaro.phonecleaner.PhoneCleanerApp
import com.ikeralvaro.phonecleaner.core.notifications.Notifications
import java.io.File

/**
 * Comprime en segundo plano (servicio en primer plano con notificación de progreso).
 *
 * El progreso se guarda por elemento (ver [CompressJobs]): si el trabajo se interrumpe y WorkManager lo
 * reintenta, continúa con los pendientes y nunca vuelve a guardar una copia ya hecha.
 */
class CompressWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PhoneCleanerApp).container
        val jobFile = File(inputData.getString(KEY_JOB_FILE) ?: return Result.failure())
        val mode = runCatching { CompressionMode.valueOf(inputData.getString(KEY_MODE).orEmpty()) }
            .getOrDefault(CompressionMode.HIGH_QUALITY)
        // Un registro de originales por ejecución (el id del trabajo se mantiene entre reintentos).
        val originalsFile = originalsFile(applicationContext, id.toString())

        val alreadyDone = CompressJobs.readOriginals(originalsFile).map { it.first }.toSet()
        val pending = CompressJobs.read(jobFile).filter { it.uri !in alreadyDone }
        val total = inputData.getInt(KEY_TOTAL, 0).takeIf { it > 0 } ?: (pending.size + alreadyDone.size)
        var done = total - pending.size

        if (pending.isNotEmpty()) {
            // Una sola vez al empezar; las actualizaciones posteriores solo cambian la notificación.
            setForeground(foregroundInfo(done, total))
        }
        for (item in pending) {
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_SAVED to savedSoFar(originalsFile)))
            updateNotification(done, total)
            val outcome = if (item.isVideo) {
                container.videoCompressor.compress(item, mode) { }
            } else {
                container.photoCompressor.compress(item, mode)
            }
            if (outcome is CompressionOutcome.Saved) {
                CompressJobs.appendOriginal(originalsFile, item.uri, outcome.savedBytes)
            }
            CompressJobs.remove(jobFile, item.id)
            done++
        }

        val originals = CompressJobs.readOriginals(originalsFile)
        jobFile.delete()
        return Result.success(
            workDataOf(
                KEY_SAVED to originals.sumOf { it.second },
                KEY_COMPRESSED to originals.size,
                KEY_TOTAL to total,
                KEY_ORIGINALS_FILE to originalsFile.path,
            ),
        )
    }

    private fun savedSoFar(file: File): Long = CompressJobs.readOriginals(file).sumOf { it.second }

    @SuppressLint("MissingPermission") // se comprueba areNotificationsEnabled justo antes
    private fun updateNotification(done: Int, total: Int) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(Notifications.ID_COMPRESS, Notifications.compressProgress(applicationContext, done, total))
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: el trabajo sigue igualmente.
        }
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

        fun originalsFile(context: Context, workId: String) = File(context.filesDir, "compress/originals-$workId.txt")
    }
}
