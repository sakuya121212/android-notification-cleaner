package com.example.notificationcleaner
 
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notificationcleaner.data.AppDatabase
import com.example.notificationcleaner.data.NotificationRepositoryImpl
import com.example.notificationcleaner.ui.AppNavigation
import com.example.notificationcleaner.ui.isNotificationServiceEnabled
import com.example.notificationcleaner.ui.theme.NotificationCleanerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getDatabase(this)
        val repository = NotificationRepositoryImpl(database.notificationDao(), database.appFilterDao())

        setContent {
            NotificationCleanerTheme {
                var isPermissionGranted by remember {
                    mutableStateOf(isNotificationServiceEnabled(this))
                }

                // Refresh permission state when activity resumes
                val lifecycleOwner = LocalLifecycleOwner.current
                remember(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isPermissionGranted = isNotificationServiceEnabled(this)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    observer
                }

                AppNavigation(
                    repository = repository,
                    isPermissionGranted = isPermissionGranted
                )
            }
        }
    }
}
