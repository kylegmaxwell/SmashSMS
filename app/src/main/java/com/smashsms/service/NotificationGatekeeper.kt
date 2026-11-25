package com.smashsms.service

import android.app.Notification
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import com.smashsms.filter.ContactChecker
import java.util.concurrent.ConcurrentHashMap

internal object NotificationGatekeeper {
    const val STALE_POST_WINDOW_MS = 15_000L

    enum class AlertAction {
        SOUND_AND_VIBRATE,
        VIBRATE_ONLY,
        SILENT,
    }

    data class ExtractedNotification(
        val senderDisplay: String?,
        val senderNumber: String?,
        val body: String,
        val latestSignature: String,
    )

    class AlertTracker(
        private val stalePostWindowMs: Long = STALE_POST_WINDOW_MS,
    ) {
        private val lastAlertedSignatureByKey = ConcurrentHashMap<String, String>()

        fun shouldAlert(
            key: String,
            flags: Int,
            postTime: Long,
            nowMs: Long,
            latestSignature: String,
        ): Boolean {
            if (postTime > 0 && nowMs - postTime > stalePostWindowMs) {
                return false
            }
            val previousSignature = lastAlertedSignatureByKey[key]
            if (previousSignature == latestSignature) {
                return false
            }
            if ((flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0 && previousSignature != null) {
                return false
            }
            lastAlertedSignatureByKey[key] = latestSignature
            return true
        }

        fun onRemoved(key: String) {
            lastAlertedSignatureByKey.remove(key)
        }

        fun clear() {
            lastAlertedSignatureByKey.clear()
        }
    }

    fun decideAlertAction(allowedByInterruptionFilter: Boolean, ringerMode: Int): AlertAction {
        if (!allowedByInterruptionFilter) return AlertAction.SILENT
        return when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AlertAction.SOUND_AND_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AlertAction.VIBRATE_ONLY
            else -> AlertAction.SILENT
        }
    }

    fun extractMessageContent(notification: Notification): ExtractedNotification? {
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
                val latestText = lastMessage.text?.toString()?.trim().orEmpty()
                val latestSignature = "${senderName.orEmpty()}|${lastMessage.timestamp}|$latestText"
                return ExtractedNotification(
                    senderDisplay = senderName,
                    senderNumber = phoneFromUri,
                    body = combinedText,
                    latestSignature = latestSignature,
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
        val latestSignature = "${title.orEmpty()}|${notification.`when`}|$body"
        return ExtractedNotification(
            senderDisplay = title,
            senderNumber = title?.takeIf { ContactChecker.looksLikePhoneNumber(it) },
            body = body,
            latestSignature = latestSignature,
        )
    }
}
