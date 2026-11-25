package com.smashsms

import android.app.Notification
import android.media.AudioManager
import com.smashsms.service.NotificationGatekeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGatekeeperTest {

    @Test
    fun alertTrackerSuppressesDuplicatesStalePostsAndOnlyAlertOnceUpdates() {
        val tracker = NotificationGatekeeper.AlertTracker(stalePostWindowMs = 15_000L)
        val key = "0|com.google.android.apps.messaging|1|null|100"

        // First fresh notification alerts.
        assertTrue(
            tracker.shouldAlert(
                key = key,
                flags = 0,
                postTime = 10_000L,
                nowMs = 10_500L,
                latestSignature = "Sender|10000|Hello",
            ),
        )

        // Exact duplicate re-post is suppressed.
        assertFalse(
            tracker.shouldAlert(
                key = key,
                flags = 0,
                postTime = 10_600L,
                nowMs = 11_000L,
                latestSignature = "Sender|10000|Hello",
            ),
        )

        // Update flagged FLAG_ONLY_ALERT_ONCE for an already alerted key is suppressed.
        assertFalse(
            tracker.shouldAlert(
                key = key,
                flags = Notification.FLAG_ONLY_ALERT_ONCE,
                postTime = 11_000L,
                nowMs = 11_100L,
                latestSignature = "Sender|11000|Updated",
            ),
        )

        // New message in the same thread without FLAG_ONLY_ALERT_ONCE alerts.
        assertTrue(
            tracker.shouldAlert(
                key = key,
                flags = 0,
                postTime = 12_000L,
                nowMs = 12_100L,
                latestSignature = "Sender|12000|Second message",
            ),
        )

        // Removing and receiving a new notification resets state.
        tracker.onRemoved(key)
        assertTrue(
            tracker.shouldAlert(
                key = key,
                flags = Notification.FLAG_ONLY_ALERT_ONCE,
                postTime = 13_000L,
                nowMs = 13_100L,
                latestSignature = "Sender|12000|Second message",
            ),
        )

        // Stale notification older than window is ignored.
        assertFalse(
            tracker.shouldAlert(
                key = "other-key",
                flags = 0,
                postTime = 1_000L,
                nowMs = 20_000L,
                latestSignature = "Sender|1000|Old",
            ),
        )
    }

    @Test
    fun decideAlertActionRespectsRingerModeAndInterruptionFilter() {
        assertEquals(
            NotificationGatekeeper.AlertAction.SOUND_AND_VIBRATE,
            NotificationGatekeeper.decideAlertAction(
                allowedByInterruptionFilter = true,
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
            ),
        )
        assertEquals(
            NotificationGatekeeper.AlertAction.VIBRATE_ONLY,
            NotificationGatekeeper.decideAlertAction(
                allowedByInterruptionFilter = true,
                ringerMode = AudioManager.RINGER_MODE_VIBRATE,
            ),
        )
        assertEquals(
            NotificationGatekeeper.AlertAction.SILENT,
            NotificationGatekeeper.decideAlertAction(
                allowedByInterruptionFilter = true,
                ringerMode = AudioManager.RINGER_MODE_SILENT,
            ),
        )
        assertEquals(
            NotificationGatekeeper.AlertAction.SILENT,
            NotificationGatekeeper.decideAlertAction(
                allowedByInterruptionFilter = false,
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
            ),
        )
        assertEquals(
            NotificationGatekeeper.AlertAction.SILENT,
            NotificationGatekeeper.decideAlertAction(
                allowedByInterruptionFilter = false,
                ringerMode = AudioManager.RINGER_MODE_VIBRATE,
            ),
        )
    }
}
