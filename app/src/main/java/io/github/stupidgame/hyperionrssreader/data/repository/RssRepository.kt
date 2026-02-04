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

    suspend fun saveFeed(feed: RssFeed, originalUrl: String, folderId: Int? = null): FeedEntity {
        return withContext(Dispatchers.IO) {
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

            if (folderId == null) {
                try {
                    notificationHelper.createChannelForFeed(savedEntity)
                } catch (e: Exception) {
                    // Ignore notification channel creation errors
                }
            }
            savedEntity
        }
    }

    suspend fun updateFeed(feed: FeedEntity) {
        withContext(Dispatchers.IO) {
            rssDao.updateFeed(feed)
            try {
                if (feed.folderId == null) {
                    notificationHelper.updateChannelForFeed(feed)
                }
            } catch (e: Exception) {
                // Ignore notification channel update errors
            }
        }
    }

    suspend fun updateFolder(folder: FolderEntity) {
        withContext(Dispatchers.IO) {
            rssDao.updateFolder(folder)
            try {
                notificationHelper.updateChannelForFolder(folder)
            } catch (e: Exception) {
                // Ignore notification channel update errors
            }
        }
    }

    suspend fun deleteFeed(feed: FeedEntity) {
        withContext(Dispatchers.IO) {
            rssDao.deleteFeed(feed)
            try {
                notificationHelper.deleteChannelForFeed(feed.id)
            } catch (e: Exception) {
                // Ignore notification channel deletion errors
            }
        }
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        withContext(Dispatchers.IO) {
            try {
                notificationHelper.deleteChannelForFolder(folder.id)
            } catch (e: Exception) {
                // Ignore notification channel deletion errors
            }
            rssDao.deleteFolder(folder)
        }
    }

    suspend fun addFolder(name: String) {
        withContext(Dispatchers.IO) {
            val id = rssDao.insertFolder(FolderEntity(name = name))
            val folder = FolderEntity(id = id.toInt(), name = name)
            try {
                notificationHelper.createChannelForFolder(folder)
            } catch (e: Exception) {
                // Ignore notification channel creation errors
            }
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

    suspend fun fetchMergedFeedContent(folderId: Int): RssFeed {
        return withContext(Dispatchers.IO) {
            val feeds = getFeedsInFolderSync(folderId)
            val folder = rssDao.getFolder(folderId)
            val folderName = folder?.name ?: "Folder Feeds"

            if (feeds.isEmpty()) {
                return@withContext RssFeed("", RssChannel(folderName, "", "No feeds in this folder", emptyList()))
            }

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

            val allItems = results.flatMap { it.channel.items }
                .distinctBy { it.link }
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
        feeds.filter { it.folderId == null }.forEach { 
            try {
                notificationHelper.createChannelForFeed(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        folders.forEach { 
            try {
                notificationHelper.createChannelForFolder(it)
            } catch (e: Exception) {
                 // Ignore
            }
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
            } catch (e: Exception) { }
        }
        return 0L
    }
}