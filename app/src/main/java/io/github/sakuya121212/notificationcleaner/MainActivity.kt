package io.github.sakuya121212.notificationcleaner
 
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sakuya121212.notificationcleaner.data.AppDatabase
import io.github.sakuya121212.notificationcleaner.data.NotificationRepositoryImpl
import io.github.sakuya121212.notificationcleaner.ui.AppNavigation
import io.github.sakuya121212.notificationcleaner.ui.isNotificationCleanerReady
import io.github.sakuya121212.notificationcleaner.ui.theme.NotificationCleanerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getDatabase(this)
        val repository = NotificationRepositoryImpl(database.notificationDao(), database.appFilterDao())

        setContent {
            NotificationCleanerTheme {
                var isPermissionGranted by remember {
                    mutableStateOf(isNotificationCleanerReady(this@MainActivity))
                }

                // Refresh permission state when activity resumes
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isPermissionGranted = isNotificationCleanerReady(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                AppNavigation(
                    repository = repository,
                    isPermissionGranted = isPermissionGranted
                )
            }
        }
    }
}
