package io.github.txnparse

/**
 * Rejects bank messages that mention money but didn't move any:
 * OTPs, bill reminders, collect requests, failed or scheduled payments.
 */
public class IgnoreFilter(private val rules: List<Pair<String, Regex>>) {

    /** Returns the reason the message should be ignored, or null to keep parsing. */
    public fun reasonToIgnore(text: String): String? =
        rules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first

    /** Returns a filter with an extra rule appended. */
    public fun plus(reason: String, regex: Regex): IgnoreFilter = IgnoreFilter(rules + (reason to regex))

    public companion object {
        private fun r(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

        @JvmField
        public val DEFAULT: IgnoreFilter = IgnoreFilter(
            listOf(
                "otp" to r("\\b(OTP|one[ -]time password|verification code)\\b"),
                "failed" to r("\\b(failed|declined|unsuccessful|could not be processed)\\b"),
                "request" to r("\\b(has requested|requested money|collect request|payment request)\\b"),
                "scheduled" to r("\\b(will be|to be|shall be) (debited|deducted|charged)\\b"),
                "due" to r("\\b(is due|due on|due date|minimum (amount )?due|total (amount|amt) due)\\b"),
            ),
        )

        @JvmField
        public val NONE: IgnoreFilter = IgnoreFilter(emptyList())
    }
}
