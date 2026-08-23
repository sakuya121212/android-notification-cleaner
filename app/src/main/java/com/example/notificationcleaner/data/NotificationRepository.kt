package com.example.notificationcleaner.data

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    val allNotifications: Flow<List<NotificationEntity>>
    val allAppFilters: Flow<List<AppFilterEntity>>

    suspend fun insert(notification: NotificationEntity): Long
    suspend fun delete(notification: NotificationEntity)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll()
    suspend fun getCount(): Int
    suspend fun findByKey(key: String): NotificationEntity?
    suspend fun getByPackageName(packageName: String): List<NotificationEntity>
    suspend fun deleteByPackageName(packageName: String)

    suspend fun isCleanEnabledForPackage(packageName: String): Boolean
    suspend fun setCleanEnabled(packageName: String, isEnabled: Boolean)
    suspend fun setAllCleanEnabled(packageNames: List<String>, isEnabled: Boolean)
}

class NotificationRepositoryImpl(
    private val notificationDao: NotificationDao,
    private val appFilterDao: AppFilterDao
) : NotificationRepository {
    override val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAll()
    override val allAppFilters: Flow<List<AppFilterEntity>> = appFilterDao.getAll()

    override suspend fun insert(notification: NotificationEntity): Long {
        return notificationDao.insert(notification)
    }

    override suspend fun delete(notification: NotificationEntity) {
        notificationDao.delete(notification)
    }

    override suspend fun deleteById(id: Long) {
        notificationDao.deleteById(id)
    }

    override suspend fun deleteAll() {
        notificationDao.deleteAll()
    }

    override suspend fun getCount(): Int {
        return notificationDao.getCount()
    }

    override suspend fun findByKey(key: String): NotificationEntity? {
        return notificationDao.findByKey(key)
    }

    override suspend fun getByPackageName(packageName: String): List<NotificationEntity> {
        return notificationDao.getByPackageName(packageName)
    }

    override suspend fun deleteByPackageName(packageName: String) {
        notificationDao.deleteByPackageName(packageName)
    }

    override suspend fun isCleanEnabledForPackage(packageName: String): Boolean {
        // Defaults to true if no explicit user preference is recorded
        return appFilterDao.isCleanEnabled(packageName) ?: true
    }

    override suspend fun setCleanEnabled(packageName: String, isEnabled: Boolean) {
        appFilterDao.insert(AppFilterEntity(packageName = packageName, isCleanEnabled = isEnabled))
    }

    override suspend fun setAllCleanEnabled(packageNames: List<String>, isEnabled: Boolean) {
        val filters = packageNames.map { AppFilterEntity(packageName = it, isCleanEnabled = isEnabled) }
        appFilterDao.insertAll(filters)
    }
}
