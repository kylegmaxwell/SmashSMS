package com.smashsms.filter

object SpamDetector {

    data class SpamRule(
        val name: String,
        val regex: Regex,
    )

    data class SpamMatch(
        val isSpam: Boolean,
        val matchedRule: String? = null,
    )

    private val rules: List<SpamRule> = listOf(
        SpamRule("ActBlue", Regex("actblue", RegexOption.IGNORE_CASE)),
        SpamRule("WinRed", Regex("winred", RegexOption.IGNORE_CASE)),
        SpamRule("NGP VAN", Regex("ngpvan", RegexOption.IGNORE_CASE)),
        SpamRule("End2End", Regex("""\bend\s*2\s*end\b""", RegexOption.IGNORE_CASE)),
        SpamRule("Stop2End", Regex("""\bstop\s*2\s*end\b""", RegexOption.IGNORE_CASE)),
        SpamRule("Stop to End", Regex("""\bstop\s+to\s+end\b""", RegexOption.IGNORE_CASE)),
        SpamRule(
            "Opt-Out Prompt",
            Regex(
                """\b(?:(?:reply|txt|text)\s+)?(?:stop|end)\s+to\s+(?:opt[\s-]*out|quit|cancel|unsubscribe|end)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        SpamRule("Reply STOP/END", Regex("""\b(?:reply|txt|text)\s+(?:stop|end)\b""", RegexOption.IGNORE_CASE)),
        SpamRule("Chip In", Regex("""\bchip\s+in\b""", RegexOption.IGNORE_CASE)),
        SpamRule("Match Multiplier", Regex("""\b(?:\d{2,4}%|\d+x)\s*match\b""", RegexOption.IGNORE_CASE)),
        SpamRule("FEC Deadline", Regex("""\bfec\s+deadline\b""", RegexOption.IGNORE_CASE)),
        SpamRule(
            "Fundraising Deadline",
            Regex(
                """\b(end[-\s]+of[-\s]+quarter|mid[-\s]+month\s+deadline|urgent\s+match|triple\s+match|quadruple\s+match|grassroots\s+donors|donate\s+any\s+amount)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        SpamRule(
            "PAC Shortlink",
            Regex("""\b[a-z0-9-]+pac\.(?:com|org|net)/\S+""", RegexOption.IGNORE_CASE),
        ),
        SpamRule(
            "Realtor Cold Text",
            Regex(
                """\bworking\s+with\s+realtors\b|\bplans\s+of\s+(?:moving|selling)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
    )

    fun detect(text: String?): SpamMatch {
        if (text.isNullOrBlank()) return SpamMatch(isSpam = false)
        for (rule in rules) {
            if (rule.regex.containsMatchIn(text)) {
                return SpamMatch(isSpam = true, matchedRule = rule.name)
            }
        }
        return SpamMatch(isSpam = false)
    }

    fun isPoliticalSpam(text: String?): Boolean = detect(text).isSpam
}
