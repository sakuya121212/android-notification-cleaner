package com.example.notificationcleaner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_filters")
data class AppFilterEntity(
    @PrimaryKey val packageName: String,
    val isCleanEnabled: Boolean = true
)
