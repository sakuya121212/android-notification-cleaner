package io.github.sakuya121212.notificationcleaner.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.sakuya121212.notificationcleaner.R
import io.github.sakuya121212.notificationcleaner.data.NotificationEntity
import io.github.sakuya121212.notificationcleaner.data.NotificationRepository
import io.github.sakuya121212.notificationcleaner.service.CleanerNotificationListenerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository,
    private val application: Application? = null
) : ViewModel() {

    // Keep notifications from excluded apps out of the cleaner UI as well as out
    // of its status-bar summary. They remain stored so they can return immediately
    // if the app is enabled for cleaning again.
    val notifications: StateFlow<List<NotificationEntity>> = combine(
        repository.allNotifications,
        repository.allAppFilters
    ) { notifications, filters ->
        val enabledByPackage = filters.associate { it.packageName to it.isCleanEnabled }
        notifications.filter { enabledByPackage[it.packageName] ?: false }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun deleteNotification(notification: NotificationEntity) {
        viewModelScope.launch {
            repository.delete(notification)
            CleanerNotificationListenerService.removeOriginalContentIntent(notification.key)
            if (repository.getCount() == 0) {
                cancelSummaryNotification()
            }
        }
    }

    fun openNotification(notification: NotificationEntity) {
        val context = application ?: return
        if (CleanerNotificationListenerService.sendOriginalContentIntent(notification.key)) return

        // A PendingIntent cannot be retained after the app process is recreated.
        // In that case, still take the user to the notification's source app.
        context.packageManager.getLaunchIntentForPackage(notification.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            _messages.tryEmit(context.getString(R.string.message_opened_source_app))
        } ?: _messages.tryEmit(context.getString(R.string.message_cannot_open_source_app))
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.deleteAll()
            CleanerNotificationListenerService.clearOriginalContentIntents()
            cancelSummaryNotification()
        }
    }

    private fun cancelSummaryNotification() {
        application?.let {
            val notificationManager = it.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(CleanerNotificationListenerService.SUMMARY_NOTIFICATION_ID)
        }
    }

    companion object {
        fun provideFactory(
            repository: NotificationRepository,
            application: Application? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotificationViewModel(repository, application) as T
            }
        }
    }
}
