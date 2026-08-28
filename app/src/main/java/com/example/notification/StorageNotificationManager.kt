package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.formatFileSize

object StorageNotificationManager {
    const val CHANNEL_ID = "low_storage_alert_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Storage Capacity Alerts"
            val descriptionText = "Notifications sent when available device storage drops below 10%"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendLowStorageNotification(context: Context, freeBytes: Long, totalBytes: Long) {
        createNotificationChannel(context)

        val freePercentage = if (totalBytes > 0) ((freeBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_START_SCAN", true)
            putExtra("EXTRA_TARGET_TAB", "SMART_CLEAN")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Low Storage Warning: ${freePercentage}% Free Space!")
            .setContentText("Available storage is critically low (${formatFileSize(freeBytes)} left). Tap to run an AI deep clean.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Available storage has dropped below 10% (${formatFileSize(freeBytes)} left of ${formatFileSize(totalBytes)}). Tap here to launch a deep clean and recover gigabytes instantly.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
