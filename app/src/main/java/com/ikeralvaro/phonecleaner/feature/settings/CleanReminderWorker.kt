package com.ikeralvaro.phonecleaner.feature.settings

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ikeralvaro.phonecleaner.PhoneCleanerApp
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.core.notifications.Notifications
import com.ikeralvaro.phonecleaner.core.util.formatBytes
import java.util.concurrent.TimeUnit

/** Una vez por semana mide la basura que se puede limpiar y, si merece la pena, avisa con una notificación. */
class CleanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PhoneCleanerApp).container
        val permissions = container.permissionManager.snapshot()
        if (!permissions.notifications) return Result.success()

        var junk = 0L
        if (permissions.allFiles) {
            junk += container.junkScanner.scan { }
                .filterKeys { it.preselected }
                .values.sumOf { items -> items.sumOf { it.sizeBytes } }
        }
        if (junk >= MIN_BYTES_TO_NOTIFY) notify(junk)
        return Result.success()
    }

    @Suppress("MissingPermission") // comprobado arriba con permissions.notifications
    private fun notify(bytes: Long) {
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.reminder_title))
            .setContentText(applicationContext.getString(R.string.reminder_text, formatBytes(bytes)))
            .setContentIntent(Notifications.openAppIntent(applicationContext))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(Notifications.ID_REMINDER, notification)
    }

    companion object {
        private const val UNIQUE_NAME = "clean_reminder"
        private const val MIN_BYTES_TO_NOTIFY = 200_000_000L

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CleanReminderWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(3, TimeUnit.DAYS)
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

