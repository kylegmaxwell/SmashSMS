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

    private data class IncomingSms(
        val sender: String,
        val body: String,
        val timestamp: Long,
        val matchedRule: String,
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

        val spamMatches = groupedBodies.mapNotNull { (sender, bodyBuilder) ->
            val body = bodyBuilder.toString()
            if (ContactChecker.isContact(context, sender)) {
                return@mapNotNull null
            }
            val match = SpamDetector.detect(body)
            if (!match.isSpam) return@mapNotNull null
            IncomingSms(
                sender = sender,
                body = body,
                timestamp = timestamps[sender] ?: System.currentTimeMillis(),
                matchedRule = match.matchedRule ?: "Unknown",
            )
        }

        if (spamMatches.isEmpty()) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Allow Android's Telephony stack time to persist the incoming SMS row first.
                delay(TELEPHONY_WRITE_DELAY)
                val dao = SmashSmsDatabase.getInstance(appContext).quarantinedMessageDao()
                for (item in spamMatches) {
                    val deleted = ShizukuHelper.deleteSmsByAddress(item.sender)
                    dao.insert(
                        QuarantinedMessage(
                            sender = item.sender,
                            body = item.body,
                            timestamp = item.timestamp,
                            matchedRule = item.matchedRule,
                            source = "SMS",
                            deletedFromTelephony = deleted,
                        ),
                    )
                    Log.i(
                        TAG,
                        "Quarantined SMS from ${item.sender} (rule=${item.matchedRule}, deleted=$deleted)",
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
        private val TELEPHONY_WRITE_DELAY = 1500.milliseconds
    }
}
