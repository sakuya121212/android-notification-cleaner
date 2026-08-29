package io.github.sakuya121212.notificationcleaner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class NotificationSummaryStats(
    val totalCount: Int,
    val latestPostTime: Long?
)

data class NotificationSummary(
    val totalCount: Int,
    val latestPostTime: Long?,
    val previewPackageNames: List<String>,
    val hasMoreApps: Boolean
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

    @Transaction
    suspend fun insertAndTrim(notification: NotificationEntity, cutoffTime: Long, maxEntries: Int): Long {
        val existing = findByKey(notification.key)
        val id = insert(notification.copy(id = existing?.id ?: notification.id))
        deleteOlderThan(cutoffTime)
        deleteExceedingLimit(maxEntries)
        return id
    }

    @Query(
        """
        SELECT COUNT(*) AS totalCount, MAX(n.postTime) AS latestPostTime
        FROM notifications AS n
        INNER JOIN app_filters AS filters ON filters.packageName = n.packageName
        WHERE filters.isCleanEnabled = 1
        """
    )
    suspend fun getCleanNotificationSummaryStats(): NotificationSummaryStats

    @Query(
        """
        SELECT n.packageName
        FROM notifications AS n
        INNER JOIN app_filters AS filters ON filters.packageName = n.packageName
        WHERE filters.isCleanEnabled = 1
        GROUP BY n.packageName
        ORDER BY MAX(n.postTime) DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentCleanPackageNames(limit: Int): List<String>

    @Transaction
    suspend fun getCleanNotificationSummary(previewLimit: Int): NotificationSummary {
        val stats = getCleanNotificationSummaryStats()
        val packageNames = getRecentCleanPackageNames(previewLimit + 1)
        return NotificationSummary(
            totalCount = stats.totalCount,
            latestPostTime = stats.latestPostTime,
            previewPackageNames = packageNames.take(previewLimit),
            hasMoreApps = packageNames.size > previewLimit
        )
    }
}
