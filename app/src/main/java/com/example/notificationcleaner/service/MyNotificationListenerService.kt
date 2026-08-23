package com.example.notificationcleaner.service

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
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.notificationcleaner.MainActivity
import com.example.notificationcleaner.R
import com.example.notificationcleaner.data.AppDatabase
import com.example.notificationcleaner.data.NotificationEntity
import com.example.notificationcleaner.data.NotificationRepository
import com.example.notificationcleaner.data.NotificationRepositoryImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

class MyNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: NotificationRepository by lazy {
        val database = AppDatabase.getDatabase(this)
        NotificationRepositoryImpl(database.notificationDao(), database.appFilterDao())
    }

    private val channelId = "cleaner_channel"
    private val summaryNotificationId = 1001

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

        // Skip ongoing or important notifications (calls, alarms, nav, etc.)
        if (shouldSkipNotification(sbn)) {
            Log.d(TAG, "Skipping important/ongoing notification from $packageName")
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

        if (!repository.isCleanEnabledForPackage(packageName)) {
            Log.d(TAG, "Auto-clean disabled for $packageName, skipping.")
            return
        }

        // Retain the original action while this process is alive so tapping an
        // item in the cleaner can behave like tapping the source notification.
        sbn.notification.contentIntent?.let { originalContentIntents[notificationKey] = it }
            ?: originalContentIntents.remove(notificationKey)

        // Intercept and cancel clearable notification.
        cancelNotification(notificationKey)

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
        updateSummaryNotification()
    }

    private fun shouldSkipNotification(sbn: StatusBarNotification): Boolean {
        // Do not intercept ongoing (non-clearable/foreground service) notifications
        if (sbn.isOngoing || !sbn.isClearable) return true

        val notification = sbn.notification ?: return false
        val category = notification.category

        // Preserve critical user communications and alerts
        return when (category) {
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_SYSTEM -> true
            else -> false
        }
    }

    private suspend fun updateSummaryNotification() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Log.w(TAG, "Notification permission not granted, skipping summary notification.")
            return
        }

        val recentNotifications = repository.allNotifications.first()
            .filter { repository.isCleanEnabledForPackage(it.packageName) }
        val count = recentNotifications.size
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (count == 0) {
            notificationManager.cancel(summaryNotificationId)
            return
        }

        val recentNotificationPreviews = recentNotifications.take(3)

        // Intent to launch MainActivity when "クリーン" button or notification is clicked
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ACTION_OPEN_CLEAN", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteViews = RemoteViews(packageName, R.layout.notification_summary).apply {
            setTextViewText(R.id.notification_title, "NCleaner")
            val formattedCount = Html.fromHtml(
                "ジャンク通知: <font color='#FF5252'><b>$count</b></font>",
                Html.FROM_HTML_MODE_LEGACY
            )
            setTextViewText(R.id.notification_count_text, formattedCount)
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
            notificationManager.notify(summaryNotificationId, summaryNotification)
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
        val name = "NCleaner"
        val descriptionText = "Channel for notification summary"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn?.packageName}")
    }

    companion object {
        private const val TAG = "MyNotificationListener"
        private val originalContentIntents = ConcurrentHashMap<String, PendingIntent>()

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
