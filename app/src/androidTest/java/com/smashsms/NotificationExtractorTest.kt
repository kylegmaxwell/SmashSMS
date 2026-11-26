package com.smashsms

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smashsms.service.NotificationGatekeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertFalse(extracted.hasImage)
    }

    @Test
    fun extractsMessagingStyleImageAttachmentWithoutCaption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sender = Person.Builder()
            .setName("+15552223333")
            .setUri("tel:+15552223333")
            .build()
        val me = Person.Builder().setName("Me").build()
        val message = NotificationCompat.MessagingStyle.Message("", 123456L, sender)
            .setData("image/jpeg", Uri.parse("content://com.google.android.apps.messaging/part/1"))

        val notification = NotificationCompat.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(NotificationCompat.MessagingStyle(me).addMessage(message))
            .build()

        val extracted = NotificationGatekeeper.extractMessageContent(notification)
        assertNotNull(extracted)
        assertEquals("+15552223333", extracted!!.senderNumber)
        assertEquals(NotificationGatekeeper.IMAGE_ATTACHMENT_PLACEHOLDER, extracted.body)
        assertTrue(extracted.hasImage)
    }

    @Test
    fun extractsMessagingStyleImageAttachmentWithCaption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sender = Person.Builder()
            .setName("+15552223333")
            .setUri("tel:+15552223333")
            .build()
        val me = Person.Builder().setName("Me").build()
        val message = NotificationCompat.MessagingStyle.Message("Check this flyer", 123456L, sender)
            .setData("image/png", Uri.parse("content://com.google.android.apps.messaging/part/2"))

        val notification = NotificationCompat.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(NotificationCompat.MessagingStyle(me).addMessage(message))
            .build()

        val extracted = NotificationGatekeeper.extractMessageContent(notification)
        assertNotNull(extracted)
        assertEquals("Check this flyer", extracted!!.body)
        assertTrue(extracted.hasImage)
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
        assertFalse(extracted.hasImage)
    }

    @Test
    fun returnsNullWhenNotificationHasNoMessageText() {
        val notification = Notification()
        assertNull(NotificationGatekeeper.extractMessageContent(notification))
    }
}
