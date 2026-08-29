package io.github.sakuya121212.notificationcleaner.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.sakuya121212.notificationcleaner.R
import io.github.sakuya121212.notificationcleaner.service.CleanerNotificationListenerService
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    var isListenerGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isPostNotificationGranted by remember {
        mutableStateOf(canPostSummaryNotifications(context))
    }

    var hasRequestedPostNotification by rememberSaveable { mutableStateOf(false) }
    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRequestedPostNotification = true
        isPostNotificationGranted = isGranted
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerGranted = isNotificationServiceEnabled(context)
                isPostNotificationGranted = canPostSummaryNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isListenerGranted, isPostNotificationGranted) {
        if (isListenerGranted && isPostNotificationGranted) {
            onPermissionGranted()
        }
    }

    PermissionOnboarding(
        isListenerGranted = isListenerGranted,
        isPostNotificationGranted = isPostNotificationGranted,
        onOpenListenerSettings = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            context.startActivity(intent)
        },
        onRequestPostNotification = {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val isSummaryChannelBlocked = notificationManager
                .getNotificationChannel(CleanerNotificationListenerService.SUMMARY_NOTIFICATION_CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE

            if (isSummaryChannelBlocked) {
                context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(
                        Settings.EXTRA_CHANNEL_ID,
                        CleanerNotificationListenerService.SUMMARY_NOTIFICATION_CHANNEL_ID
                    )
                })
                return@PermissionOnboarding
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (hasRequestedPostNotification && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                } else {
                    postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }
        },
        isPostNotificationPermanentlyDenied = hasRequestedPostNotification && !isPostNotificationGranted &&
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS
            )
    )
}

@Composable
fun PermissionOnboarding(
    isListenerGranted: Boolean,
    isPostNotificationGranted: Boolean,
    onOpenListenerSettings: () -> Unit,
    onRequestPostNotification: () -> Unit,
    isPostNotificationPermanentlyDenied: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.listener_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.listener_permission_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onOpenListenerSettings,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isListenerGranted,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isListenerGranted) stringResource(R.string.listener_permission_granted) else stringResource(R.string.listener_permission_action),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (!isPostNotificationGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                    text = stringResource(R.string.post_notification_permission_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(if (isPostNotificationPermanentlyDenied) {
                            R.string.post_notification_permission_settings_description
                        } else R.string.post_notification_permission_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onRequestPostNotification,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(if (isPostNotificationPermanentlyDenied) {
                            R.string.post_notification_permission_settings_action
                        } else R.string.post_notification_permission_action), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
    return packageNames.contains(context.packageName)
}

fun isNotificationCleanerReady(context: Context): Boolean {
    return isNotificationServiceEnabled(context) && canPostSummaryNotifications(context)
}

private fun canPostSummaryNotifications(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = notificationManager.getNotificationChannel(
        CleanerNotificationListenerService.SUMMARY_NOTIFICATION_CHANNEL_ID
    ) ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}
