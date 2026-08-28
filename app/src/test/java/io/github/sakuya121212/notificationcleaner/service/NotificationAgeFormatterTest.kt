package io.github.sakuya121212.notificationcleaner.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAgeFormatterTest {
    private val now = 2_000_000_000L

    @Test
    fun formatsElapsedTimeUsingMinutesHoursAndDays() {
        assertEquals("1分未満", NotificationAgeFormatter.format(now - 30_000L, now))
        assertEquals("5分前", NotificationAgeFormatter.format(now - 5 * 60_000L, now))
        assertEquals("1時間前", NotificationAgeFormatter.format(now - 60 * 60_000L, now))
        assertEquals("2時間15分前", NotificationAgeFormatter.format(now - 135 * 60_000L, now))
        assertEquals("2日前", NotificationAgeFormatter.format(now - 2 * 24 * 60 * 60_000L, now))
    }

    @Test
    fun futurePostTimeIsTreatedAsJustNow() {
        assertEquals("1分未満", NotificationAgeFormatter.format(now + 60_000L, now))
    }
}
