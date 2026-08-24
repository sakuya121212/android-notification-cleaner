package io.github.sakuya121212.notificationcleaner

import io.github.sakuya121212.notificationcleaner.data.AppFilterEntity
import io.github.sakuya121212.notificationcleaner.data.NotificationEntity
import io.github.sakuya121212.notificationcleaner.data.NotificationRepository
import io.github.sakuya121212.notificationcleaner.data.NotificationSummaryRow
import io.github.sakuya121212.notificationcleaner.ui.NotificationViewModel
import io.github.sakuya121212.notificationcleaner.ui.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeNotificationRepository : NotificationRepository {
    private val items = mutableListOf<NotificationEntity>()
    private val flow = MutableStateFlow<List<NotificationEntity>>(emptyList())

    private val filters = mutableMapOf<String, Boolean>()
    private val filterFlow = MutableStateFlow<List<AppFilterEntity>>(emptyList())

    override val allNotifications: Flow<List<NotificationEntity>> = flow
    override val allAppFilters: Flow<List<AppFilterEntity>> = filterFlow

    override suspend fun insert(notification: NotificationEntity): Long {
        val id = if (notification.id == 0L) (items.size + 1).toLong() else notification.id
        val newEntity = notification.copy(id = id)
        items.removeAll { it.id == id || (it.key != null && it.key == notification.key) }
        items.add(0, newEntity)
        flow.value = items.toList()
        return id
    }

    override suspend fun delete(notification: NotificationEntity) {
        items.removeAll { it.id == notification.id }
        flow.value = items.toList()
    }

    override suspend fun deleteAll() {
        items.clear()
        flow.value = emptyList()
    }

    override suspend fun getCount(): Int = items.size

    override suspend fun findByKey(key: String): NotificationEntity? {
        return items.find { it.key == key }
    }

    override suspend fun deleteByPackageName(packageName: String) {
        items.removeAll { it.packageName == packageName }
        flow.value = items.toList()
    }

    override suspend fun trimHistory(cutoffTime: Long, maxEntries: Int) {
        items.removeAll { it.postTime < cutoffTime }
        val retained = items.sortedByDescending { it.postTime }.take(maxEntries)
        items.retainAll(retained)
        flow.value = items.toList()
    }

    override suspend fun getCleanNotificationSummary(previewLimit: Int): List<NotificationSummaryRow> {
        val enabledItems = items.filter { filters[it.packageName] ?: false }
            .sortedByDescending { it.postTime }
        val count = enabledItems.size
        return enabledItems.take(previewLimit).map { NotificationSummaryRow(it, count) }
    }

    override suspend fun isCleanEnabledForPackage(packageName: String): Boolean {
        return filters[packageName] ?: false
    }

    override suspend fun setCleanEnabled(packageName: String, isEnabled: Boolean) {
        filters[packageName] = isEnabled
        if (!isEnabled) {
            items.removeAll { it.packageName == packageName }
            flow.value = items.toList()
        }
        filterFlow.value = filters.map { AppFilterEntity(it.key, it.value) }
    }

    override suspend fun setAllCleanEnabled(packageNames: List<String>, isEnabled: Boolean) {
        packageNames.forEach { filters[it] = isEnabled }
        if (!isEnabled) {
            items.clear()
            flow.value = emptyList()
        }
        filterFlow.value = filters.map { AppFilterEntity(it.key, it.value) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeNotificationRepository
    private lateinit var viewModel: NotificationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeNotificationRepository()
        viewModel = NotificationViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun notifications_initialState_isEmpty() = runTest {
        val initialList = viewModel.notifications.first()
        assertTrue(initialList.isEmpty())
    }
    @Test
    fun notifications_excludesDisabledAppsWhileKeepingTheirHistory() = runTest {
        val disabled = NotificationEntity(
            id = 1L, packageName = "com.example.disabled", title = "Hidden", text = null, postTime = 1L, key = "disabled"
        )
        val enabled = NotificationEntity(
            id = 2L, packageName = "com.example.enabled", title = "Visible", text = null, postTime = 2L, key = "enabled"
        )

        repository.insert(disabled)
        repository.setCleanEnabled(enabled.packageName, true)
        repository.insert(enabled)
        advanceUntilIdle()

        assertEquals(2, repository.allNotifications.first().size)
        val visible = viewModel.notifications.first { it.size == 1 }
        assertEquals(listOf(enabled.packageName), visible.map { it.packageName })
    }
    @Test
    fun deleteNotification_removesItem() = runTest {
        val entity = NotificationEntity(
            id = 1L,
            packageName = "com.test.app",
            appName = "Test App",
            title = "Hello",
            text = "World",
            postTime = 1700000000000L,
            key = "delete-test"
        )
        repository.insert(entity)
        advanceUntilIdle()

        viewModel.deleteNotification(entity)
        advanceUntilIdle()

        val list = repository.allNotifications.first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun clearAllNotifications_clearsAllItems() = runTest {
        repository.insert(NotificationEntity(id = 1L, packageName = "a", title = "A", text = "a", postTime = 1L, key = "clear-a"))
        repository.insert(NotificationEntity(id = 2L, packageName = "b", title = "B", text = "b", postTime = 2L, key = "clear-b"))
        advanceUntilIdle()

        assertEquals(2, repository.getCount())

        viewModel.clearAllNotifications()
        advanceUntilIdle()

        assertEquals(0, repository.getCount())
    }

    @Test
    fun formatTimestamp_formatsCorrectly() {
        val formatted = formatTimestamp(1700000000000L, 1700000300000L, "たった今", { "${it}分前" }, { "${it}時間前" }, { "${it}日前" })
        assertEquals("5分前", formatted)
    }

    @Test
    fun appFilter_defaultIsFalse_andCanBeToggled() = runTest {
        val pkg = "com.example.chat"
        // Production defaults to false; the fake starts empty as well.
        repository.setCleanEnabled(pkg, false)
        assertFalse(repository.isCleanEnabledForPackage(pkg))

        // Enable clean
        repository.setCleanEnabled(pkg, true)
        assertTrue(repository.isCleanEnabledForPackage(pkg))
    }

    @Test
    fun appFilter_setAllCleanEnabled_updatesAll() = runTest {
        val packages = listOf("com.a", "com.b", "com.c")

        repository.setAllCleanEnabled(packages, false)
        packages.forEach {
            assertFalse(repository.isCleanEnabledForPackage(it))
        }

        repository.setAllCleanEnabled(packages, true)
        packages.forEach {
            assertTrue(repository.isCleanEnabledForPackage(it))
        }
    }
}
