package io.github.sakuya121212.notificationcleaner.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["key"], unique = true)]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String? = null,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val key: String? = null
) : Parcelable
