package com.smashsms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smashsms.data.QuarantinedMessage
import com.smashsms.ui.theme.SmashSMSTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsMissingPermissionsAndEmptyQuarantineState() {
        composeRule.setContent {
            SmashSMSTheme {
                SmashSmsContent(
                    status = SetupStatus(
                        runtimePermissionsGranted = false,
                        notificationAccessGranted = false,
                        shizukuRunning = true,
                        shizukuGranted = false,
                    ),
                    messages = emptyList(),
                    onRequestRuntimePermissions = {},
                    onOpenNotificationListenerSettings = {},
                    onRequestShizukuPermission = {},
                    onOpenGoogleMessagesNotifications = {},
                    onClearAll = {},
                )
            }
        }

        composeRule.onNodeWithText("Grant").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Authorize").assertIsDisplayed()
        composeRule.onNodeWithText("App Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Quarantined Messages (0)").assertIsDisplayed()
    }

    @Test
    fun displaysQuarantinedMessagesAndTriggersClearAll() {
        var cleared = false
        val sample = QuarantinedMessage(
            id = 1L,
            sender = "+15550192834",
            body = "Chip in now via ActBlue!",
            timestamp = 1_700_000_000_000L,
            matchedRule = "ActBlue",
            source = "SMS",
            deletedFromTelephony = true,
        )

        composeRule.setContent {
            SmashSMSTheme {
                SmashSmsContent(
                    status = SetupStatus(
                        runtimePermissionsGranted = true,
                        notificationAccessGranted = true,
                        shizukuRunning = true,
                        shizukuGranted = true,
                    ),
                    messages = listOf(sample),
                    onRequestRuntimePermissions = {},
                    onOpenNotificationListenerSettings = {},
                    onRequestShizukuPermission = {},
                    onOpenGoogleMessagesNotifications = {},
                    onClearAll = { cleared = true },
                )
            }
        }

        composeRule.onNodeWithText("Quarantined Messages (1)").assertIsDisplayed()
        composeRule.onNodeWithText("+15550192834").assertIsDisplayed()
        composeRule.onNodeWithText("ActBlue").assertIsDisplayed()
        composeRule.onNodeWithText("SMS • Deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Chip in now via ActBlue!").assertIsDisplayed()

        composeRule.onNodeWithText("Clear All").performClick()
        assertTrue(cleared)
    }
}
