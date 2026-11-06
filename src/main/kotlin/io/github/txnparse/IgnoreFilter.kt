package io.github.txnparse

/**
 * Rejects bank messages that mention money but didn't move any:
 * OTPs, bill reminders, limit changes, collect requests, failed or scheduled payments,
 * and messages written by a person rather than a bank.
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
                "due" to r("\\b(is due|due on|due date|minimum (amount )?due|total (amount|amt) due|pay (before|by))\\b|^reminder\\b"),
                "limit_change" to r("\\blimit (has been )?(increased|enhanced|revised|reduced|decreased|changed)\\b"),
                // Banks write "you" or "we"; a first-person "I paid" is a person, not a bank.
                "personal" to r("\\bI (have )?(debited|paid|sent|transferred)\\b|\\bpay me\\b"),
                // Paying a credit card bill is a transfer from your bank account, not income.
                // Counting it again would double-count the card spends.
                // Refunds ("Rs 500 credited to your credit card") don't say "payment", so they still parse.
                "card_bill_payment" to r(
                    "\\bpayment\\b.{0,60}\\b(received|credited|realised)\\b.{0,40}\\bcredit card\\b" +
                        "|\\b(received|credited|realised)\\b.{0,20}\\bpayment\\b.{0,60}\\bcredit card\\b",
                ),
            ),
        )

        @JvmField
        public val NONE: IgnoreFilter = IgnoreFilter(emptyList())
    }
}
