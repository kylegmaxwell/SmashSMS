package com.smashsms

import com.smashsms.data.CustomKeyword
import com.smashsms.data.RuleManifest
import com.smashsms.data.RuleStat
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleManifestTest {

    @Test
    fun buildJsonIncludesBuiltInRulesCustomPatternsAndOtherStats() {
        val json = RuleManifest.buildJson(
            appVersionName = "1.0",
            appVersionCode = 1L,
            builtInRuleNames = listOf("ActBlue", "Reply STOP/END"),
            stats = listOf(
                RuleStat(ruleName = "ActBlue", blockedCount = 12, overrideCount = 0),
                RuleStat(ruleName = "Reply STOP/END", blockedCount = 5, overrideCount = 3),
                RuleStat(ruleName = "Keyword: crypto \"drop\"", blockedCount = 4, overrideCount = 1),
                RuleStat(ruleName = "Legacy Rule", blockedCount = 2, overrideCount = 0),
            ),
            customKeywords = listOf(
                CustomKeyword(id = 1L, keyword = "crypto \"drop\"", isRegex = false, enabled = true),
                CustomKeyword(id = 2L, keyword = """\bsolar\b""", isRegex = true, enabled = false),
            ),
            allowedSendersCount = 3,
            quarantinedMessagesCount = 19,
            exportedAtIso = "2026-11-25T18:00:00Z",
        )

        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"exportedAt\": \"2026-11-25T18:00:00Z\""))
        assertTrue(json.contains("\"totalBlocks\": 23"))
        assertTrue(json.contains("\"totalOverrides\": 4"))
        assertTrue(json.contains("\"allowedSendersCount\": 3"))
        assertTrue(json.contains("\"quarantinedMessagesCount\": 19"))
        assertTrue(json.contains("{ \"name\": \"ActBlue\", \"blockedCount\": 12, \"overrideCount\": 0 }"))
        assertTrue(json.contains("{ \"name\": \"Reply STOP/END\", \"blockedCount\": 5, \"overrideCount\": 3 }"))
        assertTrue(
            json.contains(
                "{ \"keyword\": \"crypto \\\"drop\\\"\", \"isRegex\": false, \"enabled\": true, \"blockedCount\": 4, \"overrideCount\": 1 }",
            ),
        )
        assertTrue(
            json.contains(
                "{ \"keyword\": \"\\\\bsolar\\\\b\", \"isRegex\": true, \"enabled\": false, \"blockedCount\": 0, \"overrideCount\": 0 }",
            ),
        )
        assertTrue(json.contains("{ \"ruleName\": \"Legacy Rule\", \"blockedCount\": 2, \"overrideCount\": 0 }"))
    }
}
