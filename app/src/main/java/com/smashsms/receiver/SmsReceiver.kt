package com.smashsms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.smashsms.data.QuarantinedMessage
import com.smashsms.data.SmashSmsDatabase
import com.smashsms.filter.ContactChecker
import com.smashsms.filter.SpamDetector
import com.smashsms.shizuku.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SmsReceiver : BroadcastReceiver() {

    private data class CandidateSms(
        val sender: String,
        val body: String,
        val timestamp: Long,
    )

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Reassemble multi-part SMS PDUs grouped by originating sender.
        val groupedBodies = linkedMapOf<String, StringBuilder>()
        val timestamps = hashMapOf<String, Long>()
        for (sms in messages) {
            val sender = sms.originatingAddress?.trim().orEmpty()
            if (sender.isEmpty()) continue
            val bodyPart = sms.displayMessageBody ?: sms.messageBody ?: ""
            groupedBodies.getOrPut(sender) { StringBuilder() }.append(bodyPart)
            timestamps.putIfAbsent(sender, sms.timestampMillis)
        }

        val candidates = groupedBodies.mapNotNull { (sender, bodyBuilder) ->
            if (ContactChecker.isContact(context, sender)) {
                return@mapNotNull null
            }
            CandidateSms(
                sender = sender,
                body = bodyBuilder.toString(),
                timestamp = timestamps[sender] ?: System.currentTimeMillis(),
            )
        }

        if (candidates.isEmpty()) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = SmashSmsDatabase.getInstance(appContext)
                val allowedSenders = db.allowedSenderDao().getAll()
                val customKeywords = db.customKeywordDao().getEnabled()

                val spamMatches = candidates.mapNotNull { candidate ->
                    if (allowedSenders.any { it.matches(candidate.sender) }) {
                        return@mapNotNull null
                    }
                    val match = SpamDetector.detect(candidate.body, customKeywords)
                    if (!match.isSpam) return@mapNotNull null
                    candidate to (match.matchedRule ?: "Unknown")
                }

                if (spamMatches.isEmpty()) return@launch

                // Allow Android's Telephony stack time to persist the incoming SMS row first.
                delay(TELEPHONY_WRITE_DELAY)
                val dao = db.quarantinedMessageDao()
                for ((item, matchedRule) in spamMatches) {
                    val deleted = ShizukuHelper.deleteSmsByAddress(item.sender)
                    val normalizedSender = com.smashsms.data.AllowedSender.normalize(item.sender)
                    val existing = dao.getRecent(item.timestamp - DEDUP_WINDOW_MS).firstOrNull {
                        com.smashsms.data.AllowedSender.normalize(it.sender) == normalizedSender &&
                            it.body == item.body
                    }
                    if (existing != null) {
                        dao.updateDeliveryStatus(
                            id = existing.id,
                            sender = item.sender,
                            source = "SMS",
                            deletedFromTelephony = deleted || existing.deletedFromTelephony,
                        )
                    } else {
                        dao.insert(
                            QuarantinedMessage(
                                sender = item.sender,
                                body = item.body,
                                timestamp = item.timestamp,
                                matchedRule = matchedRule,
                                source = "SMS",
                                deletedFromTelephony = deleted,
                            ),
                        )
                    }
                    Log.i(
                        TAG,
                        "Quarantined SMS from ${item.sender} (rule=$matchedRule, deleted=$deleted)",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling incoming spam SMS", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
        private const val DEDUP_WINDOW_MS = 15_000L
        private val TELEPHONY_WRITE_DELAY = 1500.milliseconds
    }
}
