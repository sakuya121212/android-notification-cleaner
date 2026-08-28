package io.github.sakuya121212.notificationcleaner.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.sakuya121212.notificationcleaner.data.NotificationRepository

@Composable
fun AppNavigation(
    repository: NotificationRepository,
    isPermissionGranted: Boolean
) {
    val context = LocalContext.current
    val initialKey = if (isPermissionGranted) {
        AppDestinations.NotificationLog
    } else {
        AppDestinations.PermissionCheck
    }

    val backStack = rememberNavBackStack(initialKey)

    // Sync backStack when permission state changes (e.g. granted from Settings or revoked)
    LaunchedEffect(isPermissionGranted) {
        if (isPermissionGranted) {
            if (backStack.contains(AppDestinations.PermissionCheck)) {
                backStack.clear()
                backStack.add(AppDestinations.NotificationLog)
            }
        } else {
            if (!backStack.contains(AppDestinations.PermissionCheck)) {
                backStack.clear()
                backStack.add(AppDestinations.PermissionCheck)
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
        entryProvider = { key ->
            when (key) {
                is AppDestinations.PermissionCheck -> {
                    NavEntry(key) {
                        PermissionScreen(
                            onPermissionGranted = {
                                if (backStack.lastOrNull() != AppDestinations.NotificationLog) {
                                    backStack.clear()
                                    backStack.add(AppDestinations.NotificationLog)
                                }
                            }
                        )
                    }
                }
                is AppDestinations.NotificationLog -> {
                    NavEntry(key) {
                        val application = context.applicationContext as Application
                        val viewModel: NotificationViewModel = viewModel(
                            factory = NotificationViewModel.provideFactory(repository, application)
                        )
                        NotificationLogScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = {
                                backStack.add(AppDestinations.Settings)
                            }
                        )
                    }
                }
                is AppDestinations.Settings -> {
                    NavEntry(key) {
                        val application = context.applicationContext as Application
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.provideFactory(application, repository)
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.size - 1)
                                }
                            }
                        )
                    }
                }
                else -> error("Unknown key: $key")
            }
        }
    )
}
