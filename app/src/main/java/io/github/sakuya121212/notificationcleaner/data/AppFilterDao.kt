package io.github.sakuya121212.notificationcleaner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppFilterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: AppFilterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(filters: List<AppFilterEntity>)

    @Query("SELECT * FROM app_filters")
    fun getAll(): Flow<List<AppFilterEntity>>

    @Query("SELECT * FROM app_filters WHERE packageName = :packageName LIMIT 1")
    suspend fun getFilter(packageName: String): AppFilterEntity?

    @Query("SELECT isCleanEnabled FROM app_filters WHERE packageName = :packageName LIMIT 1")
    suspend fun isCleanEnabled(packageName: String): Boolean?

    @Query("DELETE FROM app_filters")
    suspend fun deleteAll()
}
