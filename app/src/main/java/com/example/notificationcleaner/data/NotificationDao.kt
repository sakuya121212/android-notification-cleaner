package com.example.notificationcleaner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY postTime DESC")
    suspend fun getByPackageName(packageName: String): List<NotificationEntity>

    @Query("DELETE FROM notifications WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
