package com.smashsms.service

import android.app.Notification
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smashsms.data.QuarantinedMessage
import com.smashsms.data.SmashSmsDatabase
import com.smashsms.filter.ContactChecker
import com.smashsms.filter.SpamDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SpamNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alertTracker = NotificationGatekeeper.AlertTracker()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != GOOGLE_MESSAGES_PACKAGE) return
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extracted = NotificationGatekeeper.extractMessageContent(notification) ?: return
        val (senderDisplay, senderNumber, body, latestSignature) = extracted
        val isContact = isSavedContact(senderNumber, senderDisplay)

        serviceScope.launch {
            try {
                if (!isContact) {
                    val db = SmashSmsDatabase.getInstance(applicationContext)
                    val allowedSenders = db.allowedSenderDao().getAll()
                    val isAllowed = allowedSenders.any {
                        it.matches(senderNumber) || it.matches(senderDisplay)
                    }
                    if (!isAllowed) {
                        val customKeywords = db.customKeywordDao().getEnabled()
                        val match = SpamDetector.detect(body, customKeywords)
                        if (match.isSpam) {
                            cancelNotification(sbn.key)
                            alertTracker.onRemoved(sbn.key)
                            val displaySender = senderNumber ?: senderDisplay ?: "Unknown"
                            val matchedRule = match.matchedRule ?: "Unknown"
                            Log.i(
                                TAG,
                                "Dismissed Google Messages notification from $displaySender (rule=$matchedRule)",
                            )
                            logDismissedNotification(
                                sender = displaySender,
                                body = body,
                                timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                                matchedRule = matchedRule,
                            )
                            return@launch
                        }
                    }
                }

                // Clean message (saved contact, allowed sender, group chat, or non-spam): gatekeeper alert.
                val shouldAlert = alertTracker.shouldAlert(
                    key = sbn.key,
                    flags = notification.flags,
                    postTime = sbn.postTime,
                    nowMs = System.currentTimeMillis(),
                    latestSignature = latestSignature,
                )
                if (shouldAlert) {
                    alertCleanMessage(sbn)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error handling notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.packageName == GOOGLE_MESSAGES_PACKAGE) {
            alertTracker.onRemoved(sbn.key)
        }
    }

    override fun onDestroy() {
        alertTracker.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun alertCleanMessage(sbn: StatusBarNotification) {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        val action = NotificationGatekeeper.decideAlertAction(
            allowedByInterruptionFilter = isAllowedByInterruptionFilter(sbn),
            ringerMode = audioManager.ringerMode,
        )
        when (action) {
            NotificationGatekeeper.AlertAction.SOUND_AND_VIBRATE -> {
                playDefaultNotificationSound()
                triggerNotificationVibration()
            }
            NotificationGatekeeper.AlertAction.VIBRATE_ONLY -> {
                triggerNotificationVibration()
            }
            NotificationGatekeeper.AlertAction.SILENT -> Unit
        }
    }

    private fun isAllowedByInterruptionFilter(sbn: StatusBarNotification): Boolean {
        return when (currentInterruptionFilter) {
            INTERRUPTION_FILTER_ALL -> true
            INTERRUPTION_FILTER_NONE, INTERRUPTION_FILTER_ALARMS -> false
            INTERRUPTION_FILTER_PRIORITY -> {
                val ranking = Ranking()
                val rankingMap = currentRanking
                if (rankingMap != null && rankingMap.getRanking(sbn.key, ranking)) {
                    ranking.matchesInterruptionFilter()
                } else {
                    false
                }
            }
            else -> true
        }
    }

    private fun playDefaultNotificationSound() {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(this, soundUri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (e: Exception) {
            Log.w(TAG, "Failed playing gatekeeper notification sound", e)
        }
    }

    private fun triggerNotificationVibration() {
        try {
            val vibratorManager = getSystemService(VibratorManager::class.java) ?: return
            val vibrator = vibratorManager.defaultVibrator
            if (!vibrator.hasVibrator()) return

            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN_MS, -1)
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                .build()
            vibrator.vibrate(effect, attributes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed triggering gatekeeper vibration", e)
        }
    }

    private suspend fun logDismissedNotification(
        sender: String,
        body: String,
        timestamp: Long,
        matchedRule: String,
    ) {
        try {
            val dao = SmashSmsDatabase.getInstance(applicationContext).quarantinedMessageDao()
            val normalizedSender = com.smashsms.data.AllowedSender.normalize(sender)
            val alreadyLogged = dao.getRecent(timestamp - DEDUP_WINDOW_MS).any {
                com.smashsms.data.AllowedSender.normalize(it.sender) == normalizedSender &&
                    it.body == body
            }
            if (!alreadyLogged) {
                dao.insert(
                    QuarantinedMessage(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        matchedRule = matchedRule,
                        source = "Notification",
                        deletedFromTelephony = false,
                    ),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed logging dismissed notification", e)
        }
    }

    private fun isSavedContact(senderNumber: String?, senderDisplay: String?): Boolean {
        if (!senderNumber.isNullOrBlank() && ContactChecker.isContact(this, senderNumber)) {
            return true
        }
        if (!senderDisplay.isNullOrBlank()) {
            if (ContactChecker.looksLikePhoneNumber(senderDisplay)) {
                return ContactChecker.isContact(this, senderDisplay)
            }
            if (ContactChecker.isContactByDisplayName(this, senderDisplay)) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "SpamNotificationListener"
        private const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        private const val DEDUP_WINDOW_MS = 10_000L
        private val VIBRATION_PATTERN_MS = longArrayOf(0L, 200L, 100L, 200L)
    }
}
