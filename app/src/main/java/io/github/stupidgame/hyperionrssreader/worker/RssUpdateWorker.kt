package io.github.stupidgame.hyperionrssreader.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.stupidgame.hyperionrssreader.data.local.RssDatabase
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedParser
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedService
import io.github.stupidgame.hyperionrssreader.data.repository.NotificationHelper
import io.github.stupidgame.hyperionrssreader.data.repository.RssRepository
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class RssUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = RssDatabase.getDatabase(applicationContext)
        val okHttpClient = OkHttpClient.Builder().build()
        val rssFeedParser = RssFeedParser()
        val rssFeedService = RssFeedService(okHttpClient, rssFeedParser)
        val notificationHelper = NotificationHelper(applicationContext)
        val repository = RssRepository(database.rssDao(), rssFeedService, notificationHelper)

        return try {
            val feeds = repository.getAllFeedsSync()
            
            feeds.forEach { feed ->
                try {
                    // Use rssUrl for update check
                    val fetchedFeed = repository.fetchFeedContent(feed)
                    
                    val latestItem = fetchedFeed.channel.items.firstOrNull()
                    if (latestItem != null) {
                        val pubDateMillis = parsePubDate(latestItem.pubDate)
                        
                        if (pubDateMillis > feed.lastUpdated) {
                            if (feed.notificationEnabled) {
                                val channelId = if (feed.folderId != null) {
                                    notificationHelper.getFolderChannelId(feed.folderId)
                                } else {
                                    notificationHelper.getFeedChannelId(feed.id)
                                }
                                
                                notificationHelper.postNotification(
                                    title = "New: ${feed.title}",
                                    message = latestItem.title,
                                    url = latestItem.link,
                                    channelId = channelId,
                                    notificationId = feed.id
                                )
                            }
                            repository.updateFeed(feed.copy(lastUpdated = pubDateMillis))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    private fun parsePubDate(dateString: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                return sdf.parse(dateString)?.time ?: 0L
            } catch (e: Exception) {
                // continue
            }
        }
        return System.currentTimeMillis()
    }
}