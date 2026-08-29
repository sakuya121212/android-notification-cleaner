package io.github.sakuya121212.notificationcleaner.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var notificationDao: NotificationDao
    private lateinit var appFilterDao: AppFilterDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        notificationDao = database.notificationDao()
        appFilterDao = database.appFilterDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun trimHistory_keepsTimestampAtCutoffAndOnlyNewestEntries() = runBlocking {
        insert("expired", postTime = 99)
        insert("at-cutoff", postTime = 100)
        insert("newer", postTime = 101)
        notificationDao.deleteOlderThan(cutoffTime = 100)
        assertNull(notificationDao.findByKey("expired"))
        assertEquals("at-cutoff", notificationDao.findByKey("at-cutoff")?.key)

        insert("newest", postTime = 102)
        notificationDao.deleteExceedingLimit(limit = 2)
        assertEquals(2, notificationDao.getCount())
        assertNull(notificationDao.findByKey("at-cutoff"))
        assertEquals("newer", notificationDao.findByKey("newer")?.key)
        assertEquals("newest", notificationDao.findByKey("newest")?.key)
    }

    @Test
    fun insert_withExistingKey_replacesStoredNotification() = runBlocking {
        insert("same-key", postTime = 1, title = "old")
        insert("same-key", postTime = 2, title = "new")

        assertEquals(1, notificationDao.getCount())
        assertEquals("new", notificationDao.findByKey("same-key")?.title)
    }

    @Test
    fun cleanSummary_joinsEnabledFiltersAndReturnsDistinctRecentApps() = runBlocking {
        appFilterDao.insert(AppFilterEntity("enabled", isCleanEnabled = true))
        appFilterDao.insert(AppFilterEntity("also-enabled", isCleanEnabled = true))
        appFilterDao.insert(AppFilterEntity("disabled", isCleanEnabled = false))
        insert("old", packageName = "enabled", postTime = 1)
        insert("new", packageName = "enabled", postTime = 2)
        insert("newest", packageName = "also-enabled", postTime = 4)
        insert("hidden", packageName = "disabled", postTime = 3)

        val rows = notificationDao.getCleanNotificationSummary(previewLimit = 1)
        assertEquals(3, rows.totalCount)
        assertEquals(4L, rows.latestPostTime)
        assertEquals(listOf("also-enabled"), rows.previewPackageNames)
        assertTrue(rows.hasMoreApps)
    }

    private suspend fun insert(
        key: String,
        packageName: String = "enabled",
        postTime: Long,
        title: String? = null
    ) {
        notificationDao.insert(
            NotificationEntity(
                packageName = packageName,
                title = title,
                text = null,
                postTime = postTime,
                key = key
            )
        )
    }
}
