package io.github.stupidgame.hyperionrssreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {
    @Query("SELECT * FROM feeds")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: FeedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Delete
    suspend fun deleteFeed(feed: FeedEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
    
    @Query("SELECT * FROM feeds WHERE folderId IS NULL")
    fun getUncategorizedFeeds(): Flow<List<FeedEntity>>
    
    @Query("SELECT * FROM feeds WHERE folderId = :folderId")
    fun getFeedsInFolder(folderId: Int): Flow<List<FeedEntity>>
}