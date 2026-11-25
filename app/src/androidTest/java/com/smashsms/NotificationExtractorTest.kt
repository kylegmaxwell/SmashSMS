package com.smashsms

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smashsms.service.NotificationGatekeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationExtractorTest {

    @Test
    fun extractsMessagingStyleWithTelUriAndTimestamp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sender = Person.Builder()
            .setName("+1 (555) 222-3333")
            .setUri("tel:+15552223333")
            .build()
        val me = Person.Builder().setName("Me").build()

        val notification = NotificationCompat.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(
                NotificationCompat.MessagingStyle(me)
                    .addMessage("Urgent FEC deadline! Chip in now End2End", 123456L, sender),
            )
            .build()

        val extracted = NotificationGatekeeper.extractMessageContent(notification)
        assertNotNull(extracted)
        assertEquals("+15552223333", extracted!!.senderNumber)
        assertEquals("+1 (555) 222-3333", extracted.senderDisplay)
        assertEquals("Urgent FEC deadline! Chip in now End2End", extracted.body)
        assertEquals("+1 (555) 222-3333|123456|Urgent FEC deadline! Chip in now End2End", extracted.latestSignature)
    }

    @Test
    fun extractsStandardTitleAndBigTextFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = NotificationCompat.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Alice Smith")
            .setContentText("Short")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Hey, want to grab lunch?"))
            .setWhen(98765L)
            .build()

        val extracted = NotificationGatekeeper.extractMessageContent(notification)
        assertNotNull(extracted)
        assertEquals("Alice Smith", extracted!!.senderDisplay)
        assertNull(extracted.senderNumber)
        assertEquals("Hey, want to grab lunch?", extracted.body)
        assertEquals("Alice Smith|98765|Hey, want to grab lunch?", extracted.latestSignature)
    }

    @Test
    fun returnsNullWhenNotificationHasNoMessageText() {
        val notification = Notification()
        assertNull(NotificationGatekeeper.extractMessageContent(notification))
    }
}
