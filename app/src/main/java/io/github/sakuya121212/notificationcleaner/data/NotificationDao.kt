package io.github.sakuya121212.notificationcleaner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class NotificationSummaryRow(
    @Embedded val notification: NotificationEntity,
    val totalCount: Int
)

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int

    @Query("DELETE FROM notifications WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM notifications WHERE postTime < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("DELETE FROM notifications WHERE id IN (SELECT id FROM notifications ORDER BY postTime DESC LIMIT -1 OFFSET :limit)")
    suspend fun deleteExceedingLimit(limit: Int)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query(
        """
        SELECT n.*, (
            SELECT COUNT(*)
            FROM notifications AS count_notifications
            INNER JOIN app_filters AS count_filters
                ON count_filters.packageName = count_notifications.packageName
            WHERE count_filters.isCleanEnabled = 1
        ) AS totalCount
        FROM notifications AS n
        INNER JOIN app_filters AS filters ON filters.packageName = n.packageName
        WHERE filters.isCleanEnabled = 1
        ORDER BY n.postTime DESC
        LIMIT :previewLimit
        """
    )
    suspend fun getCleanNotificationSummary(previewLimit: Int): List<NotificationSummaryRow>
}
