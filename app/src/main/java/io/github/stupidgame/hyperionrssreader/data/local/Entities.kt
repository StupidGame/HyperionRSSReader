package io.github.stupidgame.hyperionrssreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(
    tableName = "feeds",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String, // User provided URL or Display URL
    val rssUrl: String, // Actual RSS Endpoint URL
    val title: String,
    val description: String? = null,
    val folderId: Int? = null,
    val notificationEnabled: Boolean = true,
    val lastUpdated: Long = 0
)