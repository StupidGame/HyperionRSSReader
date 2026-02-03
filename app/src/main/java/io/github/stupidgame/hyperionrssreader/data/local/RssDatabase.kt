package io.github.stupidgame.hyperionrssreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FeedEntity::class, FolderEntity::class], version = 3, exportSchema = false)
abstract class RssDatabase : RoomDatabase() {
    abstract fun rssDao(): RssDao

    companion object {
        @Volatile
        private var Instance: RssDatabase? = null

        fun getDatabase(context: Context): RssDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, RssDatabase::class.java, "rss_database")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}