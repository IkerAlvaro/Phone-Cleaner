package com.ikeralvaro.phonecleaner.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ikeralvaro.phonecleaner.MainActivity
import com.ikeralvaro.phonecleaner.R

object Notifications {
    const val CHANNEL_COMPRESS = "compress"
    const val CHANNEL_REMINDER = "reminder"
    const val ID_COMPRESS = 1001
    const val ID_REMINDER = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return // Android 7: no hay canales
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_COMPRESS, context.getString(R.string.channel_compress), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun compressProgress(context: Context, done: Int, total: Int) =
        NotificationCompat.Builder(context, CHANNEL_COMPRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.compress_notification_title))
            .setContentText(context.getString(R.string.compress_running, (done + 1).coerceAtMost(total), total))
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()
}
