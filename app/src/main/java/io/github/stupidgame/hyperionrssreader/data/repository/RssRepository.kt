package io.github.stupidgame.hyperionrssreader.data.repository

import io.github.stupidgame.hyperionrssreader.data.local.FeedEntity
import io.github.stupidgame.hyperionrssreader.data.local.FolderEntity
import io.github.stupidgame.hyperionrssreader.data.local.RssDao
import io.github.stupidgame.hyperionrssreader.data.remote.RssCandidate
import io.github.stupidgame.hyperionrssreader.data.remote.RssChannel
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeed
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedService
import io.github.stupidgame.hyperionrssreader.data.remote.RssItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class RssRepository(
    private val rssDao: RssDao,
    private val rssFeedService: RssFeedService,
    private val notificationHelper: NotificationHelper
) {
    fun getAllFeeds(): Flow<List<FeedEntity>> = rssDao.getAllFeeds()
    
    suspend fun getAllFeedsSync(): List<FeedEntity> = rssDao.getAllFeedsSync()
    
    fun getAllFolders(): Flow<List<FolderEntity>> = rssDao.getAllFolders()
    
    suspend fun getAllFoldersSync(): List<FolderEntity> = rssDao.getAllFoldersSync()
    
    fun getFeedsInFolder(folderId: Int): Flow<List<FeedEntity>> = rssDao.getFeedsInFolder(folderId)
    
    suspend fun getFeedsInFolderSync(folderId: Int): List<FeedEntity> = rssDao.getFeedsInFolderSync(folderId)
    
    fun getUncategorizedFeeds(): Flow<List<FeedEntity>> = rssDao.getUncategorizedFeeds()

    suspend fun addFeed(url: String, folderId: Int? = null) {
        withContext(Dispatchers.IO) {
            val feed = rssFeedService.fetchRssFeed(url)
            saveFeed(feed, url, folderId)
        }
    }
    
    suspend fun saveFeed(feed: RssFeed, originalUrl: String, folderId: Int? = null) {
        withContext(Dispatchers.IO) {
            val entity = FeedEntity(
                url = originalUrl, 
                rssUrl = feed.url, 
                title = feed.channel.title,
                description = feed.channel.description,
                folderId = folderId,
                lastUpdated = System.currentTimeMillis()
            )
            val id = rssDao.insertFeed(entity)
            val savedEntity = entity.copy(id = id.toInt())
            notificationHelper.createChannelForFeed(savedEntity)
        }
    }
    
    suspend fun updateFeed(feed: FeedEntity) {
        rssDao.insertFeed(feed)
    }
    
    suspend fun deleteFeed(feed: FeedEntity) {
        withContext(Dispatchers.IO) {
            rssDao.deleteFeed(feed)
        }
    }

    suspend fun addFolder(name: String) {
        withContext(Dispatchers.IO) {
            val id = rssDao.insertFolder(FolderEntity(name = name))
            val folder = FolderEntity(id = id.toInt(), name = name)
            notificationHelper.createChannelForFolder(folder)
        }
    }
    
    suspend fun fetchFeedContent(url: String): RssFeed {
        return withContext(Dispatchers.IO) {
            rssFeedService.fetchRssFeed(url)
        }
    }
    
    suspend fun fetchFeedContent(feed: FeedEntity): RssFeed {
        return withContext(Dispatchers.IO) {
            rssFeedService.fetchRssFeed(feed.rssUrl)
        }
    }
    
    // フォルダ内の全フィードを取得してマージする
    suspend fun fetchMergedFeedContent(folderId: Int): RssFeed {
        return withContext(Dispatchers.IO) {
            val feeds = getFeedsInFolderSync(folderId)
            
            // 簡易的に "Folder Feeds" とする。
            val folderName = "Folder Feeds" 

            if (feeds.isEmpty()) {
                return@withContext RssFeed("", RssChannel(folderName, "", "No feeds in this folder", emptyList()))
            }
            
            // 並列で取得
            val deferredResults = feeds.map { feed ->
                async {
                    try {
                        rssFeedService.fetchRssFeed(feed.rssUrl)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            val results = deferredResults.awaitAll().filterNotNull()
            
            // アイテムをマージして日付順（新しい順）にソート
            val allItems = results.flatMap { it.channel.items }
                .sortedByDescending { parsePubDate(it.pubDate) }
                
            RssFeed(
                url = "folder://$folderId",
                channel = RssChannel(
                    title = folderName,
                    link = "",
                    description = "Merged feed for folder. Contains ${feeds.size} feeds.",
                    items = allItems
                )
            )
        }
    }
    
    suspend fun getRssCandidates(url: String): List<RssCandidate> {
        return withContext(Dispatchers.IO) {
            rssFeedService.getRssCandidates(url)
        }
    }
    
    fun createNotificationChannelsForExistingData(feeds: List<FeedEntity>, folders: List<FolderEntity>) {
        feeds.forEach { notificationHelper.createChannelForFeed(it) }
        folders.forEach { notificationHelper.createChannelForFolder(it) }
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
            } catch (e: Exception) { }
        }
        return 0L
    }
}