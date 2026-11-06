package io.github.txnparse

import java.time.LocalDate

/**
 * Runs a rule's embedded samples and reports mismatches. Used by the test suite,
 * and handy for checking your own custom rules.
 */
public object RuleValidator {

    /** Returns a list of human-readable problems; empty means every sample passed. */
    public fun validate(rule: BankRule): List<String> {
        val problems = mutableListOf<String>()
        if (rule.samples.isEmpty()) problems += "[${rule.id}] has no samples"

        // Generic fallback off: a sample must be matched by the rule itself.
        val parser = TransactionParser(listOf(rule), useGenericFallback = false)

        rule.samples.forEachIndexed { i, sample ->
            val label = "[${rule.id}] sample $i"
            val sender = sample.sender ?: rule.senders.first()
            when (val result = parser.parse(sample.sms, sender)) {
                is ParseResult.Ignored -> problems += "$label was ignored (${result.reason})"
                ParseResult.NotATransaction -> problems += "$label matched no pattern"
                is ParseResult.Parsed -> problems += compare(label, sample.expected, result.transaction)
            }
        }
        // Every pattern must be exercised by at least one sample, or a broken regex goes unnoticed.
        rule.patterns.forEachIndexed { i, pattern ->
            val single = TransactionParser(listOf(rule.copy(patterns = listOf(pattern))), useGenericFallback = false)
            val covered = rule.samples.any { s -> single.parseOrNull(s.sms, s.sender ?: rule.senders.first()) != null }
            if (!covered) problems += "[${rule.id}] pattern $i has no sample that matches it"
        }
        return problems
    }

    private fun compare(label: String, expected: ExpectedFields, actual: Transaction): List<String> {
        val problems = mutableListOf<String>()
        fun check(field: String, want: Any?, got: Any?) {
            if (want != null && want != got) problems += "$label $field: expected <$want> but was <$got>"
        }
        check("type", expected.type, actual.type)
        val wantAmount = Text.parseAmount(expected.amount)
        if (wantAmount == null || wantAmount.compareTo(actual.amount) != 0) {
            problems += "$label amount: expected <${expected.amount}> but was <${actual.amount}>"
        }
        check("merchant", expected.merchant, actual.merchant)
        check("account", expected.account, actual.accountTail)
        expected.balance?.let { b ->
            if (Text.parseAmount(b)?.compareTo(actual.balance) != 0) {
                problems += "$label balance: expected <$b> but was <${actual.balance}>"
            }
        }
        check("date", expected.date?.let(LocalDate::parse), actual.date)
        check("ref", expected.ref, actual.reference)
        check("channel", expected.channel, actual.channel)
        return problems
    }
}
