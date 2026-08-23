package com.example.notificationcleaner.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String? = null,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val key: String? = null
) : Parcelable
