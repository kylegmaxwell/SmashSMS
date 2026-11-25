package com.smashsms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smashsms.data.AllowedSender
import com.smashsms.data.CustomKeyword
import com.smashsms.data.QuarantinedMessage
import com.smashsms.data.RuleManifest
import com.smashsms.data.RuleStat
import com.smashsms.data.RuleStatsTracker
import com.smashsms.data.SmashSmsDatabase
import com.smashsms.filter.SpamDetector
import com.smashsms.shizuku.ShizukuHelper
import com.smashsms.ui.theme.ShieldGreenContainerDark
import com.smashsms.ui.theme.ShieldGreenContainerLight
import com.smashsms.ui.theme.ShieldOnGreenContainerDark
import com.smashsms.ui.theme.ShieldOnGreenContainerLight
import com.smashsms.ui.theme.SmashSMSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

internal enum class SmashTab(
    val label: String,
    val emoji: String,
) {
    REVIEW("Review", "💥"),
    RULES("Rules", "🛡️"),
    SETUP("Setup", "⚙️"),
}

internal data class SetupStatus(
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

private fun isSmsPresentInTelephony(
    context: Context,
    sender: String,
    body: String,
): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return true
    }
    val targetKey = AllowedSender.normalize(sender)
    return try {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            var checked = 0
            while (cursor.moveToNext() && checked < 50) {
                checked++
                val addr = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                val msgBody = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                if (AllowedSender.normalize(addr) == targetKey && msgBody == body) {
                    return@use true
                }
            }
            false
        } ?: false
    } catch (_: Exception) {
        false
    }
}

@Composable
fun SmashSmsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = remember(context) { SmashSmsDatabase.getInstance(context) }
    val messageDao = remember(db) { db.quarantinedMessageDao() }
    val keywordDao = remember(db) { db.customKeywordDao() }
    val allowedDao = remember(db) { db.allowedSenderDao() }
    val ruleStatDao = remember(db) { db.ruleStatDao() }

    LaunchedEffect(db) {
        withContext(Dispatchers.IO) {
            RuleStatsTracker.syncVersionIfNeeded(context, db)
        }
    }

    val quarantinedMessages by messageDao.observeAll().collectAsState(initial = emptyList())
    val customKeywords by keywordDao.observeAll().collectAsState(initial = emptyList())
    val allowedSenders by allowedDao.observeAll().collectAsState(initial = emptyList())
    val ruleStats by ruleStatDao.observeAll().collectAsState(initial = emptyList())

    var status by remember { mutableStateOf(readSetupStatus(context)) }
    var selectedTab by rememberSaveable {
        mutableStateOf(
            if (status.runtimePermissionsGranted && status.notificationAccessGranted) {
                SmashTab.REVIEW
            } else {
                SmashTab.SETUP
            },
        )
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        status = readSetupStatus(context)
    }

    val exportManifestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val exported = withContext(Dispatchers.IO) {
                try {
                    RuleStatsTracker.syncVersionIfNeeded(context, db)
                    val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val json = RuleManifest.buildJson(
                        appVersionName = pkgInfo.versionName.orEmpty(),
                        appVersionCode = pkgInfo.longVersionCode,
                        builtInRuleNames = SpamDetector.builtInRuleNames(),
                        stats = ruleStatDao.getAll(),
                        customKeywords = customKeywords,
                        allowedSendersCount = allowedSenders.size,
                        quarantinedMessagesCount = quarantinedMessages.size,
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: return@withContext false
                    true
                } catch (_: Exception) {
                    false
                }
            }
            snackbarHostState.showSnackbar(
                if (exported) "Exported rule manifest" else "Failed to export rule manifest",
            )
        }
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
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        messages = quarantinedMessages,
        customKeywords = customKeywords,
        allowedSenders = allowedSenders,
        ruleStats = ruleStats,
        snackbarHostState = snackbarHostState,
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
        onOpenGoogleMessagesNotifications = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, "com.google.android.apps.messaging")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        },
        onAllowMessage = { message ->
            coroutineScope.launch {
                val restored = withContext(Dispatchers.IO) {
                    RuleStatsTracker.syncVersionIfNeeded(context, db)
                    val key = AllowedSender.normalize(message.sender)
                    allowedDao.deleteByNormalizedKey(key)
                    allowedDao.insert(AllowedSender(sender = message.sender))
                    ruleStatDao.incrementOverride(message.matchedRule)
                    val needsRestore = message.deletedFromTelephony ||
                        !isSmsPresentInTelephony(context, message.sender, message.body)
                    val didRestore = if (needsRestore) {
                        ShizukuHelper.restoreSmsToInbox(
                            sender = message.sender,
                            body = message.body,
                            timestamp = message.timestamp,
                        )
                    } else {
                        false
                    }
                    messageDao.deleteById(message.id)
                    didRestore
                }
                val note = if (restored) {
                    "Allowed ${message.sender} & restored to Messages — add to Contacts there"
                } else {
                    "Allowed ${message.sender} — add to Contacts in Google Messages"
                }
                snackbarHostState.showSnackbar(note)
            }
        },
        onDeleteMessage = { message ->
            coroutineScope.launch(Dispatchers.IO) {
                messageDao.deleteById(message.id)
            }
        },
        onClearAll = {
            coroutineScope.launch(Dispatchers.IO) {
                messageDao.clearAll()
            }
        },
        onAddCustomKeyword = { keyword, isRegex ->
            val trimmed = keyword.trim()
            if (trimmed.isNotEmpty()) {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        keywordDao.insert(CustomKeyword(keyword = trimmed, isRegex = isRegex))
                    }
                    snackbarHostState.showSnackbar("Added block keyword: $trimmed")
                }
            }
        },
        onToggleCustomKeyword = { id, enabled ->
            coroutineScope.launch(Dispatchers.IO) {
                keywordDao.setEnabled(id, enabled)
            }
        },
        onDeleteCustomKeyword = { id ->
            coroutineScope.launch(Dispatchers.IO) {
                keywordDao.deleteById(id)
            }
        },
        onRemoveAllowedSender = { id ->
            coroutineScope.launch(Dispatchers.IO) {
                allowedDao.deleteById(id)
            }
        },
        onExportManifest = {
            exportManifestLauncher.launch("smashsms-rule-manifest.json")
        },
        onResetRuleStats = {
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    ruleStatDao.clearAll()
                }
                snackbarHostState.showSnackbar("Reset rule block & override counts")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmashSmsContent(
    status: SetupStatus,
    selectedTab: SmashTab,
    onSelectTab: (SmashTab) -> Unit,
    messages: List<QuarantinedMessage>,
    customKeywords: List<CustomKeyword>,
    allowedSenders: List<AllowedSender>,
    ruleStats: List<RuleStat> = emptyList(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRequestRuntimePermissions: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenGoogleMessagesNotifications: () -> Unit,
    onAllowMessage: (QuarantinedMessage) -> Unit,
    onDeleteMessage: (QuarantinedMessage) -> Unit,
    onClearAll: () -> Unit,
    onAddCustomKeyword: (String, Boolean) -> Unit,
    onToggleCustomKeyword: (Long, Boolean) -> Unit,
    onDeleteCustomKeyword: (Long) -> Unit,
    onRemoveAllowedSender: (Long) -> Unit,
    onExportManifest: () -> Unit = {},
    onResetRuleStats: () -> Unit = {},
) {
    val fullyArmed = status.runtimePermissionsGranted &&
        status.notificationAccessGranted &&
        status.shizukuGranted
    val setupNeedsAttention = !fullyArmed

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "💥",
                            fontSize = 24.sp,
                        )
                        Text(
                            text = "SmashSMS",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    ProtectionPill(fullyArmed = fullyArmed)
                    Spacer(modifier = Modifier.width(12.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar {
                SmashTab.entries.forEach { tab ->
                    val showBadge = when (tab) {
                        SmashTab.REVIEW -> messages.isNotEmpty()
                        SmashTab.RULES -> false
                        SmashTab.SETUP -> setupNeedsAttention
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        icon = {
                            if (showBadge) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            if (tab == SmashTab.REVIEW) {
                                                Text("${messages.size}")
                                            }
                                        }
                                    },
                                ) {
                                    Text(text = tab.emoji, fontSize = 20.sp)
                                }
                            } else {
                                Text(text = tab.emoji, fontSize = 20.sp)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            SmashTab.REVIEW -> ReviewTabContent(
                innerPadding = innerPadding,
                status = status,
                messages = messages,
                onAllowMessage = onAllowMessage,
                onDeleteMessage = onDeleteMessage,
                onClearAll = onClearAll,
            )
            SmashTab.RULES -> RulesTabContent(
                innerPadding = innerPadding,
                customKeywords = customKeywords,
                allowedSenders = allowedSenders,
                ruleStats = ruleStats,
                onAddCustomKeyword = onAddCustomKeyword,
                onToggleCustomKeyword = onToggleCustomKeyword,
                onDeleteCustomKeyword = onDeleteCustomKeyword,
                onRemoveAllowedSender = onRemoveAllowedSender,
                onExportManifest = onExportManifest,
                onResetRuleStats = onResetRuleStats,
            )
            SmashTab.SETUP -> SetupTabContent(
                innerPadding = innerPadding,
                status = status,
                onRequestRuntimePermissions = onRequestRuntimePermissions,
                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                onRequestShizukuPermission = onRequestShizukuPermission,
                onOpenGoogleMessagesNotifications = onOpenGoogleMessagesNotifications,
            )
        }
    }
}

@Composable
private fun ReviewTabContent(
    innerPadding: PaddingValues,
    status: SetupStatus,
    messages: List<QuarantinedMessage>,
    onAllowMessage: (QuarantinedMessage) -> Unit,
    onDeleteMessage: (QuarantinedMessage) -> Unit,
    onClearAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmashHeroCard(
                status = status,
                smashedCount = messages.size,
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
                    fontWeight = FontWeight.Bold,
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
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "💥", fontSize = 36.sp)
                        Text(
                            text = "Inbox clean — no spam smashed yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "No quarantined spam yet. Messages from saved contacts are never filtered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(items = messages, key = { it.id }) { item ->
                QuarantinedMessageCard(
                    message = item,
                    canRestoreToInbox = item.deletedFromTelephony && status.shizukuGranted,
                    onAllow = { onAllowMessage(item) },
                    onDelete = { onDeleteMessage(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesTabContent(
    innerPadding: PaddingValues,
    customKeywords: List<CustomKeyword>,
    allowedSenders: List<AllowedSender>,
    ruleStats: List<RuleStat>,
    onAddCustomKeyword: (String, Boolean) -> Unit,
    onToggleCustomKeyword: (Long, Boolean) -> Unit,
    onDeleteCustomKeyword: (Long) -> Unit,
    onRemoveAllowedSender: (Long) -> Unit,
    onExportManifest: () -> Unit,
    onResetRuleStats: () -> Unit,
) {
    var newKeyword by rememberSaveable { mutableStateOf("") }
    var isRegex by rememberSaveable { mutableStateOf(false) }
    var testInput by rememberSaveable { mutableStateOf("") }
    val statsByRule = remember(ruleStats) { ruleStats.associateBy { it.ruleName } }
    val totalBlocks = remember(ruleStats) { ruleStats.sumOf { it.blockedCount } }
    val totalOverrides = remember(ruleStats) { ruleStats.sumOf { it.overrideCount } }

    val trimmedKeyword = newKeyword.trim()
    val invalidRegex = isRegex && trimmedKeyword.isNotEmpty() && !SpamDetector.isValidRegex(trimmedKeyword)
    val canAddKeyword = trimmedKeyword.isNotEmpty() && !invalidRegex

    val testMatch = remember(testInput, customKeywords) {
        if (testInput.isBlank()) null else SpamDetector.detect(testInput, customKeywords)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Custom Block Keywords",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text("Block keyword or phrase") },
                        placeholder = { Text("e.g. crypto giveaway, urgent loan") },
                        singleLine = true,
                        isError = invalidRegex,
                        supportingText = if (invalidRegex) {
                            { Text("Invalid regular expression") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isRegex,
                                onCheckedChange = { isRegex = it },
                            )
                            Text(
                                text = "Regex",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            onClick = {
                                onAddCustomKeyword(trimmedKeyword, isRegex)
                                newKeyword = ""
                                isRegex = false
                            },
                            enabled = canAddKeyword,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Add Keyword")
                        }
                    }
                }
            }
        }

        if (customKeywords.isEmpty()) {
            item {
                Text(
                    text = "No custom keywords yet. Add a word or phrase above to block it automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(items = customKeywords, key = { "kw_${it.id}" }) { keyword ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = keyword.keyword,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (keyword.isRegex) "Regex • Case-insensitive" else "Phrase • Case-insensitive",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Switch(
                                checked = keyword.enabled,
                                onCheckedChange = { onToggleCustomKeyword(keyword.id, it) },
                            )
                            TextButton(onClick = { onDeleteCustomKeyword(keyword.id) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Test Filter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = { testInput = it },
                        label = { Text("Paste a sample SMS to test") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (testMatch != null) {
                        Text(
                            text = if (testMatch.isSpam) {
                                "💥 Would smash (${testMatch.matchedRule})"
                            } else {
                                "✓ Clean — would not be blocked"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (testMatch.isSpam) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Allowed Senders (${allowedSenders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Senders allowed from Review so you can add them to Contacts in Google Messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (allowedSenders.isEmpty()) {
            item {
                Text(
                    text = "No allowed senders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(items = allowedSenders, key = { "allow_${it.id}" }) { allowed ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = allowed.sender,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(onClick = { onRemoveAllowedSender(allowed.id) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Rule Manifest & Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "$totalBlocks blocks • $totalOverrides overrides • ${customKeywords.size} custom patterns",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Export rule block/override counts and custom patterns as JSON without message text or phone numbers. Counts reset automatically on new app versions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onExportManifest,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Export Manifest")
                        }
                        TextButton(
                            onClick = onResetRuleStats,
                            enabled = totalBlocks > 0 || totalOverrides > 0,
                        ) {
                            Text("Reset Stats")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Built-in Spam Rules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpamDetector.builtInRuleNames().forEach { ruleName ->
                    val stat = statsByRule[ruleName]
                    val chipLabel = if (stat != null && (stat.blockedCount > 0 || stat.overrideCount > 0)) {
                        "$ruleName • ${stat.blockedCount}💥 ${stat.overrideCount}✓"
                    } else {
                        ruleName
                    }
                    StatusChip(
                        label = chipLabel,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupTabContent(
    innerPadding: PaddingValues,
    status: SetupStatus,
    onRequestRuntimePermissions: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenGoogleMessagesNotifications: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Setup & Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
            PermissionRowCard(
                title = "Silence Google Messages",
                subtitle = "Optional: set Incoming Messages to Silent so SmashSMS handles sound/vibration only for clean messages",
                granted = false,
                actionLabel = "App Settings",
                onClick = onOpenGoogleMessagesNotifications,
            )
        }
    }
}

@Composable
private fun ProtectionPill(fullyArmed: Boolean) {
    val dark = isSystemInDarkTheme()
    val containerColor = when {
        fullyArmed && dark -> ShieldGreenContainerDark
        fullyArmed -> ShieldGreenContainerLight
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when {
        fullyArmed && dark -> ShieldOnGreenContainerDark
        fullyArmed -> ShieldOnGreenContainerLight
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
    ) {
        Text(
            text = if (fullyArmed) "💥 Armed" else "💥 Partial",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SmashHeroCard(
    status: SetupStatus,
    smashedCount: Int,
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        ),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(18.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "💥",
                            fontSize = 28.sp,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (smashedCount == 0) {
                                "Ready to Smash Spam"
                            } else {
                                "$smashedCount Spam Smashed"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Silently blocks spam from non-contacts while keeping saved contacts safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroStatusChip(label = "SMS", active = status.runtimePermissionsGranted)
                    HeroStatusChip(label = "Listener", active = status.notificationAccessGranted)
                    HeroStatusChip(label = "Shizuku", active = status.shizukuGranted)
                }
            }
        }
    }
}

@Composable
private fun HeroStatusChip(
    label: String,
    active: Boolean,
) {
    val dark = isSystemInDarkTheme()
    val bg = when {
        active && dark -> ShieldGreenContainerDark
        active -> ShieldGreenContainerLight
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val fg = when {
        active && dark -> ShieldOnGreenContainerDark
        active -> ShieldOnGreenContainerLight
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Text(
            text = if (active) "✓ $label" else "• $label",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
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
    val dark = isSystemInDarkTheme()
    val containerColor = when {
        granted && dark -> ShieldGreenContainerDark.copy(alpha = 0.55f)
        granted -> ShieldGreenContainerLight.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val badgeColor = when {
        granted && dark -> ShieldOnGreenContainerDark
        granted -> ShieldOnGreenContainerLight
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (granted) {
            BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(actionLabel)
                }
            } else {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun QuarantinedMessageCard(
    message: QuarantinedMessage,
    canRestoreToInbox: Boolean,
    onAllow: () -> Unit,
    onDelete: () -> Unit,
) {
    val formattedTime = remember(message.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(message.timestamp))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "💥",
                            fontSize = 15.sp,
                        )
                    }
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                StatusChip(
                    label = message.matchedRule,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatusChip(
                    label = if (message.deletedFromTelephony) {
                        "${message.source} • Deleted"
                    } else {
                        "${message.source} • Dismissed"
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = onAllow,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (canRestoreToInbox) "Allow & Restore" else "Allow")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
                notificationAccessGranted = true,
                shizukuRunning = true,
                shizukuGranted = true,
            ),
            selectedTab = SmashTab.REVIEW,
            onSelectTab = {},
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
            customKeywords = listOf(
                CustomKeyword(id = 1, keyword = "crypto giveaway", isRegex = false, enabled = true),
            ),
            allowedSenders = emptyList(),
            onRequestRuntimePermissions = {},
            onOpenNotificationListenerSettings = {},
            onRequestShizukuPermission = {},
            onOpenGoogleMessagesNotifications = {},
            onAllowMessage = {},
            onDeleteMessage = {},
            onClearAll = {},
            onAddCustomKeyword = { _, _ -> },
            onToggleCustomKeyword = { _, _ -> },
            onDeleteCustomKeyword = {},
            onRemoveAllowedSender = {},
        )
    }
}
