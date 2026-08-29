package io.github.sakuya121212.notificationcleaner.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sakuya121212.notificationcleaner.data.NotificationRepository

private enum class Destination { PermissionCheck, NotificationLog, Settings }

@Composable
fun AppNavigation(
    repository: NotificationRepository,
    isPermissionGranted: Boolean
) {
    val context = LocalContext.current
    var destination by remember {
        mutableStateOf(if (isPermissionGranted) Destination.NotificationLog else Destination.PermissionCheck)
    }

    LaunchedEffect(isPermissionGranted) {
        destination = if (isPermissionGranted) {
            Destination.NotificationLog
        } else {
            Destination.PermissionCheck
        }
    }

    BackHandler(enabled = destination == Destination.Settings) {
        destination = Destination.NotificationLog
    }

    when (destination) {
        Destination.PermissionCheck -> PermissionScreen(
            onPermissionGranted = { destination = Destination.NotificationLog }
        )

        Destination.NotificationLog -> {
            val application = context.applicationContext as Application
            val notificationViewModel: NotificationViewModel = viewModel(
                factory = NotificationViewModel.provideFactory(repository, application)
            )
            NotificationLogScreen(
                viewModel = notificationViewModel,
                onNavigateToSettings = { destination = Destination.Settings }
            )
        }

        Destination.Settings -> {
            val application = context.applicationContext as Application
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(application, repository)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { destination = Destination.NotificationLog }
            )
        }
    }
}
