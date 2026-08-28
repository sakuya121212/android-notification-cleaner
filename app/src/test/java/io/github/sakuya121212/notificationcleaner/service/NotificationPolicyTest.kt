package io.github.sakuya121212.notificationcleaner.service

import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    @Test
    fun summaryChannelEnabled_rejectsBlockedChannel() {
        assertFalse(isSummaryChannelEnabled(NotificationManager.IMPORTANCE_NONE))
        assertTrue(isSummaryChannelEnabled(NotificationManager.IMPORTANCE_LOW))
        assertFalse(isSummaryChannelEnabled(null))
    }

    @Test
    fun shouldSkipNotification_preservesOngoingAndNonClearableNotifications() {
        assertTrue(shouldSkipNotification(isOngoing = true, isClearable = true, category = null))
        assertTrue(shouldSkipNotification(isOngoing = false, isClearable = false, category = null))
    }

    @Test
    fun shouldSkipNotification_preservesCriticalCategories() {
        listOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_SYSTEM
        ).forEach { category ->
            assertTrue(shouldSkipNotification(isOngoing = false, isClearable = true, category = category))
        }
    }

    @Test
    fun shouldSkipNotification_allowsClearableNonCriticalNotifications() {
        assertFalse(shouldSkipNotification(isOngoing = false, isClearable = true, category = Notification.CATEGORY_MESSAGE))
        assertFalse(shouldSkipNotification(isOngoing = false, isClearable = true, category = null))
    }
}
