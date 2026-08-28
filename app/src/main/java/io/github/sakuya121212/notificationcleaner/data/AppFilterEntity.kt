package io.github.sakuya121212.notificationcleaner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_filters")
data class AppFilterEntity(
    @PrimaryKey val packageName: String,
    val isCleanEnabled: Boolean = false
)
