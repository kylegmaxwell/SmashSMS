package com.example.smashsms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.smashsms.data.QuarantinedMessage
import com.example.smashsms.data.SmashSmsDatabase
import com.example.smashsms.shizuku.ShizukuHelper
import com.example.smashsms.ui.theme.SmashSMSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmashSMSTheme {
                SmashSmsScreen()
            }
        }
    }
}

private val REQUIRED_RUNTIME_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS,
    Manifest.permission.READ_CONTACTS,
)

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

private data class SetupStatus(
    val runtimePermissionsGranted: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
)

private fun readSetupStatus(context: Context): SetupStatus {
    val runtimeGranted = REQUIRED_RUNTIME_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    val notificationGranted = NotificationManagerCompat
        .getEnabledListenerPackages(context)
        .contains(context.packageName)
    val shizukuRunning = ShizukuHelper.isBinderReady()
    val shizukuGranted = ShizukuHelper.hasPermission()
    return SetupStatus(
        runtimePermissionsGranted = runtimeGranted,
        notificationAccessGranted = notificationGranted,
        shizukuRunning = shizukuRunning,
        shizukuGranted = shizukuGranted,
    )
}

@Composable
fun SmashSmsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember(context) { SmashSmsDatabase.getInstance(context).quarantinedMessageDao() }
    val quarantinedMessages by dao.observeAll().collectAsState(initial = emptyList())

    var status by remember { mutableStateOf(readSetupStatus(context)) }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        status = readSetupStatus(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = readSetupStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            status = readSetupStatus(context)
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            status = readSetupStatus(context)
        }
        val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                status = readSetupStatus(context)
            }
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        }
    }

    SmashSmsContent(
        status = status,
        messages = quarantinedMessages,
        onRequestRuntimePermissions = {
            runtimePermissionLauncher.launch(REQUIRED_RUNTIME_PERMISSIONS)
        },
        onOpenNotificationListenerSettings = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        },
        onRequestShizukuPermission = {
            if (ShizukuHelper.isBinderReady()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        },
        onClearAll = {
            coroutineScope.launch(Dispatchers.IO) {
                dao.clearAll()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmashSmsContent(
    status: SetupStatus,
    messages: List<QuarantinedMessage>,
    onRequestRuntimePermissions: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onClearAll: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("SmashSMS") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Setup & Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                PermissionRowCard(
                    title = "SMS & Contacts Access",
                    subtitle = if (status.runtimePermissionsGranted) {
                        "Granted (RECEIVE_SMS, READ_SMS, READ_CONTACTS)"
                    } else {
                        "Required to inspect incoming SMS and skip saved contacts"
                    },
                    granted = status.runtimePermissionsGranted,
                    actionLabel = if (status.runtimePermissionsGranted) "Granted" else "Grant",
                    onClick = onRequestRuntimePermissions,
                )
            }

            item {
                PermissionRowCard(
                    title = "Notification Listener",
                    subtitle = if (status.notificationAccessGranted) {
                        "Active for Google Messages spam dismissal"
                    } else {
                        "Required to dismiss Google Messages SMS/RCS notifications"
                    },
                    granted = status.notificationAccessGranted,
                    actionLabel = if (status.notificationAccessGranted) "Enabled" else "Open Settings",
                    onClick = onOpenNotificationListenerSettings,
                )
            }

            item {
                val shizukuSubtitle = when {
                    status.shizukuGranted -> "Authorized via Shizuku"
                    status.shizukuRunning -> "Shizuku running — tap to authorize deletion"
                    else -> "Shizuku service not detected — start Shizuku first"
                }
                val shizukuLabel = when {
                    status.shizukuGranted -> "Authorized"
                    status.shizukuRunning -> "Authorize"
                    else -> "Open Shizuku"
                }
                PermissionRowCard(
                    title = "Shizuku Access",
                    subtitle = shizukuSubtitle,
                    granted = status.shizukuGranted,
                    actionLabel = shizukuLabel,
                    onClick = onRequestShizukuPermission,
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Quarantined Messages (${messages.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (messages.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear All")
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = "No quarantined political spam yet. Messages from saved contacts are never filtered.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(items = messages, key = { it.id }) { item ->
                    QuarantinedMessageCard(message = item)
                }
            }
        }
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                OutlinedButton(onClick = onClick) {
                    Text(actionLabel)
                }
            } else {
                Button(onClick = onClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun QuarantinedMessageCard(message: QuarantinedMessage) {
    val formattedTime = remember(message.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(message.timestamp))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(label = message.matchedRule)
                StatusChip(
                    label = if (message.deletedFromTelephony) {
                        "${message.source} • Deleted"
                    } else {
                        "${message.source} • Dismissed"
                    },
                )
            }

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SmashSmsPreview() {
    SmashSMSTheme {
        SmashSmsContent(
            status = SetupStatus(
                runtimePermissionsGranted = true,
                notificationAccessGranted = false,
                shizukuRunning = true,
                shizukuGranted = false,
            ),
            messages = listOf(
                QuarantinedMessage(
                    id = 1,
                    sender = "+15550192834",
                    body = "URGENT FEC DEADLINE: Chip in now for a 400% match via ActBlue! Reply STOP to end.",
                    timestamp = 1700000000000L,
                    matchedRule = "ActBlue",
                    source = "SMS",
                    deletedFromTelephony = true,
                ),
            ),
            onRequestRuntimePermissions = {},
            onOpenNotificationListenerSettings = {},
            onRequestShizukuPermission = {},
            onClearAll = {},
        )
    }
}
