package io.github.sakuya121212.notificationcleaner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.sakuya121212.notificationcleaner.MainActivity
import io.github.sakuya121212.notificationcleaner.R
import io.github.sakuya121212.notificationcleaner.data.AppDatabase
import io.github.sakuya121212.notificationcleaner.data.NotificationEntity
import io.github.sakuya121212.notificationcleaner.data.NotificationRepository
import io.github.sakuya121212.notificationcleaner.data.NotificationRepositoryImpl
import kotlinx.coroutines.*
import java.util.Collections
import java.util.LinkedHashMap

class CleanerNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: NotificationRepository by lazy {
        val database = AppDatabase.getDatabase(this)
        NotificationRepositoryImpl(database.notificationDao(), database.appFilterDao())
    }

    private val channelId = "cleaner_channel"
    private val historyRetentionMillis = 30L * 24 * 60 * 60 * 1000
    private val maxHistoryEntries = 500

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Settings are stored in Room. Observing them here updates the summary
        // immediately even while the activity is not on screen.
        serviceScope.launch {
            repository.allAppFilters.collect {
                updateSummaryNotification()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        // onNotificationPosted is not guaranteed to be called for notifications that
        // existed before the listener was connected (for example after app launch).
        serviceScope.launch {
            activeNotifications.orEmpty().forEach { notification ->
                processNotification(notification)
            }
            updateSummaryNotification()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        serviceScope.launch {
            processNotification(sbn)
        }
    }

    private suspend fun processNotification(sbn: StatusBarNotification) {

        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return

        // Do not hide source notifications when the user cannot see the summary.
        // The app-level switch can be on while this specific channel is off.
        if (!canPostSummaryNotifications()) return

        // Skip ongoing or important notifications (calls, alarms, nav, etc.)
        if (shouldSkipNotification(sbn)) {
            Log.d(TAG, "Skipping important/ongoing notification from $packageName")
            return
        }

        if (!repository.isCleanEnabledForPackage(packageName)) {
            Log.d(TAG, "Auto-clean disabled for $packageName, skipping.")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
            ?: extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val postTime = sbn.postTime
        val notificationKey = sbn.key

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }


        val existing = repository.findByKey(notificationKey)
        val entity = NotificationEntity(
            id = existing?.id ?: 0,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            postTime = postTime,
            key = notificationKey
        )
        repository.insert(entity)

        // Save first: if Room is unavailable, leave the source notification intact.
        // Retain the original action while this process is alive so tapping an
        // item in the cleaner can behave like tapping the source notification.
        sbn.notification.contentIntent?.let { retainOriginalContentIntent(notificationKey, it) }
            ?: originalContentIntents.remove(notificationKey)

        // Intercept only after its replacement has been persisted.
        cancelNotification(notificationKey)
        repository.trimHistory(
            cutoffTime = System.currentTimeMillis() - historyRetentionMillis,
            maxEntries = maxHistoryEntries
        )
        updateSummaryNotification()
    }

    private fun shouldSkipNotification(sbn: StatusBarNotification): Boolean {
        // Do not intercept ongoing (non-clearable/foreground service) notifications
        return shouldSkipNotification(sbn.isOngoing, sbn.isClearable, sbn.notification?.category)
    }

    private suspend fun updateSummaryNotification() {
        if (!canPostSummaryNotifications()) {
            Log.w(TAG, "Summary notification channel is unavailable, skipping summary notification.")
            return
        }

        val summaryRows = repository.getCleanNotificationSummary(previewLimit = 3)
        val count = summaryRows.firstOrNull()?.totalCount ?: 0
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (count == 0) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }

        val recentNotificationPreviews = summaryRows.map { it.notification }

        // Both the summary and its button open the notification log.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteViews = RemoteViews(packageName, R.layout.notification_summary).apply {
            setTextViewText(R.id.notification_title, getString(R.string.summary_notification_title))
            setTextViewText(R.id.notification_count_text, getString(R.string.summary_notification_count, count))
            setOnClickPendingIntent(R.id.btn_clean, pendingIntent)

            // Reset icon preview visibilities
            setViewVisibility(R.id.icon_preview_1, View.GONE)
            setViewVisibility(R.id.icon_preview_2, View.GONE)
            setViewVisibility(R.id.icon_preview_3, View.GONE)
            setViewVisibility(R.id.icon_preview_more, View.GONE)

            val iconViewIds = listOf(R.id.icon_preview_1, R.id.icon_preview_2, R.id.icon_preview_3)
            recentNotificationPreviews.forEachIndexed { index, entity ->
                if (index < iconViewIds.size) {
                    try {
                        val icon = packageManager.getApplicationIcon(entity.packageName)
                        val bitmap = drawableToBitmap(icon)
                        setImageViewBitmap(iconViewIds[index], bitmap)
                        setViewVisibility(iconViewIds[index], View.VISIBLE)
                    } catch (_: Exception) {
                    }
                }
            }

            if (count > 3) {
                setViewVisibility(R.id.icon_preview_more, View.VISIBLE)
            }
        }

        val summaryNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_cleaner_bell)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to post notification due to security exception", e)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val descriptionText = getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /** A disabled channel suppresses the summary even when the app-level switch is on. */
    private fun canPostSummaryNotifications(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return isSummaryChannelEnabled(notificationManager.getNotificationChannel(channelId)?.importance)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn?.packageName}")
    }

    companion object {
        private const val TAG = "CleanerNotificationListener"
        const val SUMMARY_NOTIFICATION_ID = 1001
        private const val MAX_RETAINED_CONTENT_INTENTS = 500
        // This cache deliberately has process-local lifetime. PendingIntent cannot be
        // persisted safely, and callers fall back to launching the source app after a restart.
        private val originalContentIntents = Collections.synchronizedMap(
            object : LinkedHashMap<String, PendingIntent>(MAX_RETAINED_CONTENT_INTENTS, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>?): Boolean {
                    return size > MAX_RETAINED_CONTENT_INTENTS
                }
            }
        )

        private fun retainOriginalContentIntent(key: String, intent: PendingIntent) {
            originalContentIntents[key] = intent
        }

        fun sendOriginalContentIntent(notificationKey: String?): Boolean {
            val pendingIntent = notificationKey?.let(originalContentIntents::get) ?: return false
            return try {
                pendingIntent.send()
                true
            } catch (_: PendingIntent.CanceledException) {
                originalContentIntents.remove(notificationKey)
                false
            }
        }

        fun removeOriginalContentIntent(notificationKey: String?) {
            notificationKey?.let(originalContentIntents::remove)
        }

        fun clearOriginalContentIntents() {
            originalContentIntents.clear()
        }
    }
}

/** Pure notification-policy predicate, visible to local unit tests. */
internal fun shouldSkipNotification(
    isOngoing: Boolean,
    isClearable: Boolean,
    category: String?
): Boolean {
    if (isOngoing || !isClearable) return true

    return category in setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_STOPWATCH,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_SYSTEM
    )
}

/** Visible to local unit tests; only IMPORTANCE_NONE means the user blocked the channel. */
internal fun isSummaryChannelEnabled(importance: Int?): Boolean {
    return importance != null && importance != NotificationManager.IMPORTANCE_NONE
}
