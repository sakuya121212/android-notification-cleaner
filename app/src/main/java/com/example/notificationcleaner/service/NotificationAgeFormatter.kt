package com.example.notificationcleaner.service

import kotlin.math.max

internal object NotificationAgeFormatter {
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
    private const val DAY_MILLIS = 24L * HOUR_MILLIS

    fun format(postTimeMillis: Long, nowMillis: Long): String {
        val elapsedMillis = max(0L, nowMillis - postTimeMillis)

        return when {
            elapsedMillis < MINUTE_MILLIS -> "1分未満"
            elapsedMillis < HOUR_MILLIS -> "${elapsedMillis / MINUTE_MILLIS}分前"
            elapsedMillis < DAY_MILLIS -> {
                val hours = elapsedMillis / HOUR_MILLIS
                val minutes = (elapsedMillis % HOUR_MILLIS) / MINUTE_MILLIS
                if (minutes == 0L) "${hours}時間前" else "${hours}時間${minutes}分前"
            }
            else -> "${elapsedMillis / DAY_MILLIS}日前"
        }
    }
}
