package com.skypass.skytrack.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.skypass.skytrack.MainActivity
import com.skypass.skytrack.R

/** Shows SkyTrack pushes as system notifications. Tapping one opens the screen named in the push. */
object PushNotifier {
    const val EXTRA_OPEN_SCREEN = "com.skypass.skytrack.OPEN_SCREEN"
    private const val CHANNEL_ID = "skytrack_alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Attendance, leave and payroll", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shift reminders and updates about your attendance, leave and salary"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canShow(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context, id: String, title: String, body: String, screen: String) {
        if (!canShow(context)) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_SCREEN, screen)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(0xFFE53935.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post
        }
    }
}
