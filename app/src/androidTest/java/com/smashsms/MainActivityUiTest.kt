package com.smashsms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smashsms.data.AllowedSender
import com.smashsms.data.CustomKeyword
import com.smashsms.data.QuarantinedMessage
import com.smashsms.data.RuleStat
import com.smashsms.ui.theme.SmashSMSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setupTabShowsMissingPermissions() {
        composeRule.setContent {
            SmashSMSTheme {
                SmashSmsContent(
                    status = SetupStatus(
                        runtimePermissionsGranted = false,
                        notificationAccessGranted = false,
                        shizukuRunning = true,
                        shizukuGranted = false,
                    ),
                    selectedTab = SmashTab.SETUP,
                    onSelectTab = {},
                    messages = emptyList(),
                    customKeywords = emptyList(),
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

        composeRule.onNodeWithText("Grant").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Authorize").assertIsDisplayed()
        composeRule.onNodeWithText("App Settings").assertIsDisplayed()
    }

    @Test
    fun reviewTabDisplaysQuarantinedMessagesAndHandlesAllowAndClearAll() {
        var cleared = false
        var allowedSender: String? = null
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
                    selectedTab = SmashTab.REVIEW,
                    onSelectTab = {},
                    messages = listOf(sample),
                    customKeywords = emptyList(),
                    allowedSenders = emptyList(),
                    onRequestRuntimePermissions = {},
                    onOpenNotificationListenerSettings = {},
                    onRequestShizukuPermission = {},
                    onOpenGoogleMessagesNotifications = {},
                    onAllowMessage = { allowedSender = it.sender },
                    onDeleteMessage = {},
                    onClearAll = { cleared = true },
                    onAddCustomKeyword = { _, _ -> },
                    onToggleCustomKeyword = { _, _ -> },
                    onDeleteCustomKeyword = {},
                    onRemoveAllowedSender = {},
                )
            }
        }

        composeRule.onNodeWithText("Quarantined Messages (1)").assertIsDisplayed()
        composeRule.onNodeWithText("+15550192834").assertIsDisplayed()
        composeRule.onNodeWithText("ActBlue").assertIsDisplayed()
        composeRule.onNodeWithText("SMS • Deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Chip in now via ActBlue!").assertIsDisplayed()

        composeRule.onNodeWithText("Allow & Restore").performClick()
        assertEquals("+15550192834", allowedSender)

        composeRule.onNodeWithText("Clear All").performClick()
        assertTrue(cleared)
    }

    @Test
    fun rulesTabAddsCustomKeywordAndSwitchesTabs() {
        var addedKeyword: String? = null
        var exported = false
        var resetStats = false
        var blockUnknownImages by mutableStateOf(true)
        var currentTab by mutableStateOf(SmashTab.REVIEW)

        composeRule.setContent {
            SmashSMSTheme {
                SmashSmsContent(
                    status = SetupStatus(
                        runtimePermissionsGranted = true,
                        notificationAccessGranted = true,
                        shizukuRunning = true,
                        shizukuGranted = true,
                    ),
                    selectedTab = currentTab,
                    onSelectTab = { currentTab = it },
                    messages = emptyList(),
                    customKeywords = listOf(
                        CustomKeyword(id = 1L, keyword = "crypto giveaway"),
                    ),
                    allowedSenders = listOf(
                        AllowedSender(id = 1L, sender = "+15550192834"),
                    ),
                    ruleStats = listOf(
                        RuleStat(ruleName = "ActBlue", blockedCount = 3, overrideCount = 1),
                    ),
                    blockUnknownImages = blockUnknownImages,
                    onToggleBlockUnknownImages = { blockUnknownImages = it },
                    onRequestRuntimePermissions = {},
                    onOpenNotificationListenerSettings = {},
                    onRequestShizukuPermission = {},
                    onOpenGoogleMessagesNotifications = {},
                    onAllowMessage = {},
                    onDeleteMessage = {},
                    onClearAll = {},
                    onAddCustomKeyword = { kw, _ -> addedKeyword = kw },
                    onToggleCustomKeyword = { _, _ -> },
                    onDeleteCustomKeyword = {},
                    onRemoveAllowedSender = {},
                    onExportManifest = { exported = true },
                    onResetRuleStats = { resetStats = true },
                )
            }
        }

        composeRule.onNodeWithText("Rules").performClick()
        composeRule.onNodeWithText("Block Unknown Images").assertIsDisplayed()
        composeRule.onNodeWithText("Custom Block Keywords").assertIsDisplayed()
        composeRule.onNodeWithText("crypto giveaway").assertIsDisplayed()

        composeRule.onNodeWithText("Block keyword or phrase").performTextInput("solar rebate")
        composeRule.onNodeWithText("Add Keyword").performClick()
        assertEquals("solar rebate", addedKeyword)

        composeRule.onNodeWithText("3 blocks • 1 overrides • 1 custom patterns").assertIsDisplayed()
        composeRule.onNodeWithText("Export Manifest").performClick()
        assertTrue(exported)

        composeRule.onNodeWithText("Reset Stats").performClick()
        assertTrue(resetStats)
    }
}
