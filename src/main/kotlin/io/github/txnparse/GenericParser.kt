package io.github.txnparse

/**
 * Keyword heuristics for banks without a rule file. Deliberately conservative:
 * it needs a currency amount, a debit/credit verb and a banking word.
 */
internal object GenericParser {
    private val opts = setOf(RegexOption.IGNORE_CASE)

    private const val NUMBER = "(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)"
    private val amount = Regex("(?:rs\\.?|inr|₹)\\s*$NUMBER", opts)
    private val balance = Regex(
        "(?:avl\\.?\\s*bal(?:ance)?|available balance|avl\\.?\\s*limit|available limit|bal)\\s*(?:is)?\\s*[:\\-]?\\s*(?:rs\\.?|inr|₹)?\\s*$NUMBER",
        opts,
    )

    private val debit = Regex("\\b(debited|spent|paid|sent|withdrawn|withdrawal|purchase|debit)\\b", opts)
    private val credit = Regex("\\b(credited|received|deposited|refund(?:ed)?|credit)\\b", opts)
    private val bankingWord = Regex("\\b(a/?c|acct|account|card|upi|vpa|bank|neft|imps|rtgs|atm)\\b", opts)

    private val account = Regex("\\b(?:a/?c|acct|account|card)\\s*(?:no\\.?|number|ending)?\\s*[:\\-]?\\s*[x*]*(\\d{3,6})\\b", opts)
    private val ref = Regex("\\b(?:upi\\s*ref(?:\\s*no)?|ref(?:erence)?\\s*(?:no\\.?|number|#)?|rrn|txn\\s*id)\\s*[:\\-.]?\\s*([a-z0-9]{6,})", opts)
    private val merchant = Regex(
        "\\b(?:at|to|towards|from|by|info)\\s*[:\\-]?\\s+([A-Za-z0-9@&.'_ /-]{2,40}?)(?=\\s+(?:on|via|ref|upi|avl|bal|using|for)\\b|[.,;(]|$)",
        opts,
    )
    private val notMerchant = Regex("^(your|you|the|a/?c|acct|account|card|rs\\.?|inr|bank|\\d+)\\b", opts)

    fun parse(text: String, currency: String): Transaction? {
        if (!bankingWord.containsMatchIn(text)) return null

        val type = detectType(text) ?: return null

        // The first currency amount that isn't the balance is the transaction amount.
        val balanceMatch = balance.find(text)
        val amountMatch = amount.findAll(text).firstOrNull { m ->
            balanceMatch == null || m.range.first !in balanceMatch.range
        } ?: return null
        val value = Text.parseAmount(amountMatch.groupValues[1]) ?: return null
        if (value.signum() <= 0) return null

        return Transaction(
            type = type,
            amount = value,
            currency = currency,
            merchant = findMerchant(text),
            accountTail = account.find(text)?.groupValues?.get(1),
            balance = balanceMatch?.let { Text.parseAmount(it.groupValues[1]) },
            date = null,
            reference = ref.find(text)?.groupValues?.get(1)?.takeIf { r -> r.any(Char::isDigit) },
            channel = Text.detectChannel(text),
            bankId = null,
            confidence = Confidence.LOW,
        )
    }

    /** Whichever verb appears first decides, so "debited ... credited to merchant" is a debit. */
    private fun detectType(text: String): TransactionType? {
        val d = debit.find(text)?.range?.first
        val c = credit.find(text)?.range?.first
        return when {
            d == null && c == null -> null
            c == null -> TransactionType.DEBIT
            d == null -> TransactionType.CREDIT
            d <= c -> TransactionType.DEBIT
            else -> TransactionType.CREDIT
        }
    }

    private fun findMerchant(text: String): String? =
        merchant.findAll(text)
            .mapNotNull { Text.cleanMerchant(it.groupValues[1]) }
            .firstOrNull { !notMerchant.containsMatchIn(it) }
}
