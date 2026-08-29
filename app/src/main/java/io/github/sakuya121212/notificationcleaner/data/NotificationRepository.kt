package io.github.sakuya121212.notificationcleaner.data

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    val allNotifications: Flow<List<NotificationEntity>>
    val allAppFilters: Flow<List<AppFilterEntity>>

    suspend fun insert(notification: NotificationEntity): Long
    suspend fun insertAndTrim(notification: NotificationEntity, cutoffTime: Long, maxEntries: Int): Long
    suspend fun delete(notification: NotificationEntity)
    suspend fun deleteAll()
    suspend fun getCount(): Int
    suspend fun findByKey(key: String): NotificationEntity?
    suspend fun deleteByPackageName(packageName: String)
    suspend fun trimHistory(cutoffTime: Long, maxEntries: Int)
    suspend fun getCleanNotificationSummary(previewLimit: Int): NotificationSummary

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

    override suspend fun insert(notification: NotificationEntity) = notificationDao.insert(notification)

    override suspend fun insertAndTrim(notification: NotificationEntity, cutoffTime: Long, maxEntries: Int) =
        notificationDao.insertAndTrim(notification, cutoffTime, maxEntries)

    override suspend fun delete(notification: NotificationEntity) = notificationDao.delete(notification)

    override suspend fun deleteAll() = notificationDao.deleteAll()

    override suspend fun getCount() = notificationDao.getCount()

    override suspend fun findByKey(key: String) = notificationDao.findByKey(key)

    override suspend fun deleteByPackageName(packageName: String) = notificationDao.deleteByPackageName(packageName)

    override suspend fun trimHistory(cutoffTime: Long, maxEntries: Int) {
        notificationDao.deleteOlderThan(cutoffTime)
        notificationDao.deleteExceedingLimit(maxEntries)
    }

    override suspend fun getCleanNotificationSummary(previewLimit: Int) =
        notificationDao.getCleanNotificationSummary(previewLimit)

    override suspend fun isCleanEnabledForPackage(packageName: String): Boolean {
        // Opt in only: an app is never cleaned until the user enables it.
        return appFilterDao.isCleanEnabled(packageName) ?: false
    }

    override suspend fun setCleanEnabled(packageName: String, isEnabled: Boolean) {
        appFilterDao.insert(AppFilterEntity(packageName = packageName, isCleanEnabled = isEnabled))
        if (!isEnabled) {
            // Disabling an app also removes any previously retained private content.
            notificationDao.deleteByPackageName(packageName)
        }
    }

    override suspend fun setAllCleanEnabled(packageNames: List<String>, isEnabled: Boolean) {
        val filters = packageNames.map { AppFilterEntity(packageName = it, isCleanEnabled = isEnabled) }
        appFilterDao.insertAll(filters)
        if (!isEnabled) {
            notificationDao.deleteAll()
        }
    }
}
