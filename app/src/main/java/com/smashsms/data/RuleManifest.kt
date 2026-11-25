package com.smashsms.data

import android.content.Context
import androidx.core.content.edit
import java.time.Instant

object RuleStatsTracker {
    private const val PREFS_NAME = "smash_sms_prefs"
    private const val KEY_APP_VERSION = "rule_stats_app_version"

    @Volatile
    private var syncedVersionKey: String? = null
    private val lock = Any()

    fun currentAppVersionKey(context: Context): String {
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${pkgInfo.longVersionCode}:${pkgInfo.versionName.orEmpty()}"
    }

    suspend fun syncVersionIfNeeded(context: Context, db: SmashSmsDatabase) {
        val appContext = context.applicationContext
        val currentKey = currentAppVersionKey(appContext)
        if (syncedVersionKey == currentKey) return

        val needsReset = synchronized(lock) {
            if (syncedVersionKey == currentKey) {
                false
            } else {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val stored = prefs.getString(KEY_APP_VERSION, null)
                if (stored != currentKey) {
                    true
                } else {
                    syncedVersionKey = currentKey
                    false
                }
            }
        }

        if (needsReset) {
            db.ruleStatDao().clearAll()
            synchronized(lock) {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit { putString(KEY_APP_VERSION, currentKey) }
                syncedVersionKey = currentKey
            }
        }
    }
}

object RuleManifest {
    const val SCHEMA_VERSION = 1

    fun buildJson(
        appVersionName: String,
        appVersionCode: Long,
        builtInRuleNames: List<String>,
        stats: List<RuleStat>,
        customKeywords: List<CustomKeyword>,
        allowedSendersCount: Int,
        quarantinedMessagesCount: Int,
        exportedAtIso: String = Instant.now().toString(),
    ): String {
        val statsByRule = stats.associateBy { it.ruleName }
        val builtInSet = builtInRuleNames.toSet()
        val customStatKeys = customKeywords.map { "Keyword: ${it.keyword.trim()}" }.toSet()

        val totalBlocks = stats.sumOf { it.blockedCount }
        val totalOverrides = stats.sumOf { it.overrideCount }

        val unmatchedStats = stats.filter { stat ->
            stat.ruleName !in builtInSet && stat.ruleName !in customStatKeys
        }

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"schemaVersion\": $SCHEMA_VERSION,")
        sb.appendLine("  \"exportedAt\": ${jsonString(exportedAtIso)},")
        sb.appendLine("  \"appVersion\": ${jsonString(appVersionName)},")
        sb.appendLine("  \"appVersionCode\": $appVersionCode,")
        sb.appendLine("  \"summary\": {")
        sb.appendLine("    \"totalBlocks\": $totalBlocks,")
        sb.appendLine("    \"totalOverrides\": $totalOverrides,")
        sb.appendLine("    \"allowedSendersCount\": $allowedSendersCount,")
        sb.appendLine("    \"quarantinedMessagesCount\": $quarantinedMessagesCount")
        sb.appendLine("  },")

        sb.appendLine("  \"builtInRules\": [")
        builtInRuleNames.forEachIndexed { index, ruleName ->
            val stat = statsByRule[ruleName]
            val comma = if (index < builtInRuleNames.lastIndex) "," else ""
            sb.appendLine(
                "    { \"name\": ${jsonString(ruleName)}, " +
                    "\"blockedCount\": ${stat?.blockedCount ?: 0}, " +
                    "\"overrideCount\": ${stat?.overrideCount ?: 0} }$comma",
            )
        }
        sb.appendLine("  ],")

        sb.appendLine("  \"customBlockPatterns\": [")
        customKeywords.forEachIndexed { index, kw ->
            val stat = statsByRule["Keyword: ${kw.keyword.trim()}"]
            val comma = if (index < customKeywords.lastIndex) "," else ""
            sb.appendLine(
                "    { \"keyword\": ${jsonString(kw.keyword)}, " +
                    "\"isRegex\": ${kw.isRegex}, " +
                    "\"enabled\": ${kw.enabled}, " +
                    "\"blockedCount\": ${stat?.blockedCount ?: 0}, " +
                    "\"overrideCount\": ${stat?.overrideCount ?: 0} }$comma",
            )
        }
        sb.appendLine("  ],")

        sb.appendLine("  \"otherRuleStats\": [")
        unmatchedStats.forEachIndexed { index, stat ->
            val comma = if (index < unmatchedStats.lastIndex) "," else ""
            sb.appendLine(
                "    { \"ruleName\": ${jsonString(stat.ruleName)}, " +
                    "\"blockedCount\": ${stat.blockedCount}, " +
                    "\"overrideCount\": ${stat.overrideCount} }$comma",
            )
        }
        sb.appendLine("  ]")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonString(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        out.append(String.format("\\u%04x", ch.code))
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        out.append('"')
        return out.toString()
    }
}
