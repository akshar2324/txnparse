package io.github.txnparse

/**
 * Turns bank SMS / notification text into [Transaction]s.
 *
 * ```
 * val parser = TransactionParser.withBundledRules()
 * when (val r = parser.parse(sender = "VM-HDFCBK", body = sms)) {
 *     is ParseResult.Parsed -> save(r.transaction)
 *     is ParseResult.Ignored -> Unit          // OTP, reminder, failed payment
 *     ParseResult.NotATransaction -> Unit
 * }
 * ```
 *
 * Instances are immutable and thread-safe; build one and reuse it.
 */
public class TransactionParser(
    rules: List<BankRule>,
    /** Fall back to keyword heuristics when no bank rule matches. Results are [Confidence.LOW]. */
    private val useGenericFallback: Boolean = true,
    private val ignoreFilter: IgnoreFilter = IgnoreFilter.DEFAULT,
) {
    private class CompiledPattern(val rule: PatternRule, val regex: Regex, val dateFormats: List<String>)
    private class CompiledRule(val rule: BankRule, val senders: List<String>, val patterns: List<CompiledPattern>)

    private val compiled: List<CompiledRule> = rules.map { rule ->
        CompiledRule(
            rule = rule,
            senders = rule.senders.map(Text::normalizeSender),
            patterns = rule.patterns.map { p ->
                CompiledPattern(
                    rule = p,
                    regex = Regex(p.regex, RegexOption.IGNORE_CASE),
                    dateFormats = p.dateFormats.ifEmpty { rule.dateFormats },
                )
            },
        )
    }

    /** Ids of the loaded bank rules. */
    public val bankIds: List<String> get() = compiled.map { it.rule.id }

    /**
     * @param sender SMS sender ID or notification package, if known. When it matches a bank,
     *   only that bank's rules are tried, which avoids attributing a message to the wrong bank.
     * @param body Message text. Newlines and repeated spaces are fine.
     */
    public fun parse(body: String, sender: String? = null): ParseResult {
        val text = Text.normalizeBody(body)
        if (text.isEmpty()) return ParseResult.NotATransaction

        ignoreFilter.reasonToIgnore(text)?.let { return ParseResult.Ignored(it) }

        val candidates = candidateRules(sender)
        for (rule in candidates) {
            for (pattern in rule.patterns) {
                val match = pattern.regex.find(text) ?: continue
                val txn = toTransaction(match, text, rule.rule, pattern) ?: continue
                return ParseResult.Parsed(txn)
            }
        }

        if (useGenericFallback) {
            GenericParser.parse(text, currency = candidates.singleOrNull()?.rule?.currency ?: "INR")
                ?.let { return ParseResult.Parsed(it.copy(bankId = candidates.singleOrNull()?.rule?.id)) }
        }
        return ParseResult.NotATransaction
    }

    /** Convenience for callers that only care about successful parses. */
    public fun parseOrNull(body: String, sender: String? = null): Transaction? =
        (parse(body, sender) as? ParseResult.Parsed)?.transaction

    private fun candidateRules(sender: String?): List<CompiledRule> {
        if (sender.isNullOrBlank()) return compiled
        val normalized = Text.normalizeSender(sender)
        val bySender = compiled.filter { rule -> rule.senders.any { normalized.contains(it) } }
        return bySender.ifEmpty { compiled }
    }

    private fun toTransaction(
        match: MatchResult,
        text: String,
        rule: BankRule,
        pattern: CompiledPattern,
    ): Transaction? {
        val amount = Text.parseAmount(match.named("amount")) ?: return null
        return Transaction(
            type = pattern.rule.type,
            amount = amount,
            currency = rule.currency,
            merchant = Text.cleanMerchant(match.named("merchant")),
            accountTail = match.named("account"),
            balance = Text.parseAmount(match.named("balance")),
            date = Text.parseDate(match.named("date"), pattern.dateFormats),
            reference = match.named("ref"),
            channel = Text.detectChannel(text),
            bankId = rule.id,
            confidence = Confidence.HIGH,
        )
    }

    public companion object {
        /** A parser with every rule bundled in this library. */
        @JvmStatic
        @JvmOverloads
        public fun withBundledRules(
            extraRules: List<BankRule> = emptyList(),
            useGenericFallback: Boolean = true,
        ): TransactionParser = TransactionParser(
            // Extra rules first so callers can override a bundled format.
            rules = extraRules + RuleLoader.bundledRules(),
            useGenericFallback = useGenericFallback,
        )
    }
}
