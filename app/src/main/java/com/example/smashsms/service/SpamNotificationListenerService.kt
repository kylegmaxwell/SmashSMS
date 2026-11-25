package com.example.smashsms.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smashsms.data.QuarantinedMessage
import com.example.smashsms.data.SmashSmsDatabase
import com.example.smashsms.filter.ContactChecker
import com.example.smashsms.filter.SpamDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SpamNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != GOOGLE_MESSAGES_PACKAGE) return
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extracted = extractMessageContent(notification) ?: return
        val (senderDisplay, senderNumber, body) = extracted

        if (isSavedContact(senderNumber, senderDisplay)) return

        val match = SpamDetector.detect(body)
        if (!match.isSpam) return

        cancelNotification(sbn.key)
        val displaySender = senderNumber ?: senderDisplay ?: "Unknown"
        Log.i(TAG, "Dismissed Google Messages notification from $displaySender (rule=${match.matchedRule})")

        val timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val appContext = applicationContext
        serviceScope.launch {
            try {
                val dao = SmashSmsDatabase.getInstance(appContext).quarantinedMessageDao()
                val alreadyLogged = dao.existsRecent(
                    sender = displaySender,
                    body = body,
                    sinceTimestamp = timestamp - DEDUP_WINDOW_MS,
                )
                if (!alreadyLogged) {
                    dao.insert(
                        QuarantinedMessage(
                            sender = displaySender,
                            body = body,
                            timestamp = timestamp,
                            matchedRule = match.matchedRule ?: "Unknown",
                            source = "Notification",
                            deletedFromTelephony = false,
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed logging dismissed notification", e)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun isSavedContact(senderNumber: String?, senderDisplay: String?): Boolean {
        if (!senderNumber.isNullOrBlank() && ContactChecker.isContact(this, senderNumber)) {
            return true
        }
        if (!senderDisplay.isNullOrBlank()) {
            if (looksLikePhoneNumber(senderDisplay)) {
                return ContactChecker.isContact(this, senderDisplay)
            }
            if (ContactChecker.isContactByDisplayName(this, senderDisplay)) {
                return true
            }
        }
        return false
    }

    private fun looksLikePhoneNumber(value: String): Boolean {
        val stripped = value.trim()
        return stripped.any { it.isDigit() } && stripped.all {
            it.isDigit() || it in setOf('+', '-', '(', ')', ' ', '.')
        }
    }

    private data class ExtractedNotification(
        val senderDisplay: String?,
        val senderNumber: String?,
        val body: String,
    )

    private fun extractMessageContent(notification: Notification): ExtractedNotification? {
        val extras = notification.extras ?: return null
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            val lastMessage = messagingStyle.messages.last()
            val person = lastMessage.person
            val personUri = person?.uri
            val phoneFromUri = personUri
                ?.takeIf { it.startsWith("tel:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val senderName = person?.name?.toString()?.trim()
                ?: messagingStyle.conversationTitle?.toString()?.trim()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            val combinedText = messagingStyle.messages
                .mapNotNull { it.text?.toString()?.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            if (combinedText.isNotEmpty()) {
                return ExtractedNotification(
                    senderDisplay = senderName,
                    senderNumber = phoneFromUri,
                    body = combinedText,
                )
            }
        }

        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotEmpty() }

        val body = bigText ?: lines ?: text ?: return null
        return ExtractedNotification(
            senderDisplay = title,
            senderNumber = title?.takeIf { looksLikePhoneNumber(it) },
            body = body,
        )
    }

    companion object {
        private const val TAG = "SpamNotificationListener"
        private const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        private const val DEDUP_WINDOW_MS = 10_000L
    }
}
