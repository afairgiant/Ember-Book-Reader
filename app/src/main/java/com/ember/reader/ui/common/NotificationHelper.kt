package com.ember.reader.ui.common

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ember.reader.R

object NotificationHelper {

    private const val CHANNEL_DOWNLOADS = "ember_downloads"
    private const val CHANNEL_SYNC = "ember_sync"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Book download notifications"
        }

        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            "Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Reading progress sync notifications"
        }

        manager.createNotificationChannels(listOf(downloadChannel, syncChannel))
    }

    fun showDownloadComplete(context: Context, bookTitle: String, bookId: String) {
        if (!hasPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Complete")
            .setContentText(bookTitle)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(bookId.hashCode(), notification)
    }

    fun showSyncComplete(context: Context, message: String) {
        if (!hasPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sync Complete")
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify("sync".hashCode(), notification)
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
