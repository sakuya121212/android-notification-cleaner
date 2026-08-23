package com.example.notificationcleaner.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface AppDestinations : NavKey {
    @Serializable
    data object PermissionCheck : AppDestinations

    @Serializable
    data object NotificationLog : AppDestinations

    @Serializable
    data object Settings : AppDestinations
}
