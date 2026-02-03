package io.github.stupidgame.hyperionrssreader.data.repository

import io.github.stupidgame.hyperionrssreader.data.local.FeedEntity
import io.github.stupidgame.hyperionrssreader.data.local.FolderEntity
import io.github.stupidgame.hyperionrssreader.data.local.RssDao
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeed
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RssRepository(
    private val rssDao: RssDao,
    private val rssFeedService: RssFeedService
) {
    fun getAllFeeds(): Flow<List<FeedEntity>> = rssDao.getAllFeeds()
    
    fun getAllFolders(): Flow<List<FolderEntity>> = rssDao.getAllFolders()
    
    fun getFeedsInFolder(folderId: Int): Flow<List<FeedEntity>> = rssDao.getFeedsInFolder(folderId)
    
    fun getUncategorizedFeeds(): Flow<List<FeedEntity>> = rssDao.getUncategorizedFeeds()

    suspend fun addFeed(url: String, folderId: Int? = null) {
        withContext(Dispatchers.IO) {
            val feed = rssFeedService.fetchRssFeed(url)
            saveFeed(feed, folderId)
        }
    }
    
    suspend fun saveFeed(feed: RssFeed, folderId: Int? = null) {
        withContext(Dispatchers.IO) {
            val entity = FeedEntity(
                url = feed.url,
                title = feed.channel.title,
                description = feed.channel.description,
                folderId = folderId
            )
            rssDao.insertFeed(entity)
        }
    }

    suspend fun addFolder(name: String) {
        withContext(Dispatchers.IO) {
            rssDao.insertFolder(FolderEntity(name = name))
        }
    }
    
    suspend fun fetchFeedContent(url: String): RssFeed {
        return withContext(Dispatchers.IO) {
            rssFeedService.fetchRssFeed(url)
        }
    }
}