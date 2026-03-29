package com.ember.reader.ui.common

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ember.reader.MainActivity
import com.ember.reader.R

object NotificationHelper {

    private const val CHANNEL_DOWNLOADS = "ember_downloads"
    private const val CHANNEL_SYNC = "ember_sync"
    private const val CHANNEL_ACTIVITY = "ember_activity"

    const val NOTIFICATION_ID_DOWNLOAD_PROGRESS = 9001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Book download notifications"
        }

        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            "Sync",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reading progress sync notifications"
        }

        val activityChannel = NotificationChannel(
            CHANNEL_ACTIVITY,
            "Activity",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important updates like new books synced, auto-downloads completed"
        }

        manager.createNotificationChannels(listOf(downloadChannel, syncChannel, activityChannel))
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun showDownloadComplete(context: Context, bookTitle: String, bookId: String) {
        if (!hasPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "book_detail/$bookId")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            bookId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(bookTitle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(bookId.hashCode(), notification)
    }

    fun showAutoDownloaded(context: Context, bookTitles: List<String>) {
        if (!hasPermission(context) || bookTitles.isEmpty()) return

        val title = if (bookTitles.size == 1) {
            "Book Auto-Downloaded"
        } else {
            "${bookTitles.size} Books Auto-Downloaded"
        }

        val text = bookTitles.joinToString(", ")

        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVITY)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)

        if (bookTitles.size > 1) {
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
            bookTitles.forEach { style.addLine(it) }
            builder.setStyle(style)
        }

        NotificationManagerCompat.from(context).notify("auto_download".hashCode(), builder.build())
    }

    fun showSyncComplete(context: Context, message: String) {
        if (!hasPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Sync Complete")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify("sync".hashCode(), notification)
    }

    fun showSyncProgress(context: Context, pulled: Int, pushed: Int, downloaded: Int) {
        if (!hasPermission(context)) return
        if (pulled == 0 && pushed == 0 && downloaded == 0) return

        val parts = mutableListOf<String>()
        if (pulled > 0) parts.add("$pulled pulled")
        if (pushed > 0) parts.add("$pushed pushed")
        if (downloaded > 0) parts.add("$downloaded downloaded")
        val summary = parts.joinToString(" · ")

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Sync Complete")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify("sync_detail".hashCode(), notification)
    }

    fun showSyncError(context: Context, serverName: String, error: String) {
        if (!hasPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIVITY)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Sync Failed — $serverName")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify("sync_error_$serverName".hashCode(), notification)
    }

    fun buildDownloadProgressNotification(
        context: Context,
        bookTitle: String,
        cancelIntent: PendingIntent,
    ): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading \"$bookTitle\"")
            .setContentText("Starting download...")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()
    }

    fun updateDownloadProgress(
        context: Context,
        bookTitle: String,
        progress: Int,
        progressText: String,
        cancelIntent: PendingIntent,
    ) {
        if (!hasPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading \"$bookTitle\"")
            .setContentText(progressText)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DOWNLOAD_PROGRESS, notification)
    }

    fun showDownloadFailed(context: Context, bookTitle: String) {
        if (!hasPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(bookTitle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify("download_error".hashCode(), notification)
    }

    fun dismissDownloadProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_DOWNLOAD_PROGRESS)
    }

    private fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
