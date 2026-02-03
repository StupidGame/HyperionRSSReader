package io.github.stupidgame.hyperionrssreader.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.github.stupidgame.hyperionrssreader.R
import io.github.stupidgame.hyperionrssreader.data.local.FeedEntity
import io.github.stupidgame.hyperionrssreader.data.local.FolderEntity

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannelForFeed(feed: FeedEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getFeedChannelId(feed.id)
            val channelName = "Feed: ${feed.title}"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for ${feed.title}"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun createChannelForFolder(folder: FolderEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getFolderChannelId(folder.id)
            val channelName = "Folder: ${folder.name}"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for feeds in ${folder.name}"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getFeedChannelId(feedId: Int) = "feed_$feedId"
    fun getFolderChannelId(folderId: Int) = "folder_$folderId"

    fun openChannelSettings(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            openAppSettings()
        }
    }
    
    fun openAppSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            // Android 5-7
             val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
    
    fun postNotification(title: String, message: String, url: String, channelId: String, notificationId: Int) {
         val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
         val pendingIntent = PendingIntent.getActivity(
             context, 
             notificationId, 
             intent, 
             PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
         )

         val builder = NotificationCompat.Builder(context, channelId)
             .setSmallIcon(R.mipmap.ic_launcher) 
             .setContentTitle(title)
             .setContentText(message)
             .setPriority(NotificationCompat.PRIORITY_DEFAULT)
             .setContentIntent(pendingIntent)
             .setAutoCancel(true)

         notificationManager.notify(notificationId, builder.build())
    }
}