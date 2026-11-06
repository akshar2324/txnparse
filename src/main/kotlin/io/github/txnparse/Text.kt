package io.github.txnparse

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Small parsing helpers shared by rule and generic parsing. */
internal object Text {
    private val whitespace = Regex("\\s+")
    private val senderPrefix = Regex("^[A-Z]{2}-")
    private val senderSuffix = Regex("-[A-Z]$")

    /** Collapses newlines and repeated spaces so rules can be written on one line. */
    fun normalizeBody(body: String): String = body.replace(whitespace, " ").trim()

    /** "VM-HDFCBK-S" -> "HDFCBK". */
    fun normalizeSender(sender: String): String =
        sender.trim().uppercase(Locale.ROOT).replace(senderPrefix, "").replace(senderSuffix, "")

    /** "1,23,456.5" -> 123456.50. Returns null for anything that isn't a number. */
    fun parseAmount(raw: String?): BigDecimal? {
        val cleaned = raw?.replace(",", "")?.trim()?.trimEnd('.') ?: return null
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    }

    private val formatterCache = ConcurrentHashMap<String, DateTimeFormatter>()

    fun parseDate(raw: String?, formats: List<String>): LocalDate? {
        if (raw.isNullOrBlank()) return null
        for (pattern in formats) {
            val formatter = formatterCache.getOrPut(pattern) {
                // STRICT rejects impossible dates like 31-02 instead of rounding them.
                // STRICT needs 'u' (proleptic year) rather than 'y' (year-of-era).
                DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern(pattern.replace('y', 'u'))
                    .toFormatter(Locale.ENGLISH)
                    .withResolverStyle(ResolverStyle.STRICT)
            }
            try {
                return LocalDate.parse(raw.trim(), formatter)
            } catch (_: Exception) {
                // try the next format
            }
        }
        return null
    }

    fun cleanMerchant(raw: String?): String? =
        raw?.replace(whitespace, " ")?.trim()?.trimEnd('.', ',', ';', ':', '-')?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Ordered so the most specific keyword wins (ATM before CARD, etc.). */
    private val channelKeywords: List<Pair<Channel, Regex>> = listOf(
        Channel.ATM to Regex("\\bATM\\b", RegexOption.IGNORE_CASE),
        Channel.UPI to Regex("\\b(UPI|VPA)\\b", RegexOption.IGNORE_CASE),
        Channel.IMPS to Regex("\\bIMPS\\b", RegexOption.IGNORE_CASE),
        Channel.NEFT to Regex("\\bNEFT\\b", RegexOption.IGNORE_CASE),
        Channel.RTGS to Regex("\\bRTGS\\b", RegexOption.IGNORE_CASE),
        Channel.CARD to Regex("\\b(credit|debit)?\\s*card\\b", RegexOption.IGNORE_CASE),
    )

    fun detectChannel(body: String): Channel? =
        channelKeywords.firstOrNull { (_, regex) -> regex.containsMatchIn(body) }?.first
}

/** Named group value, or null when the group is absent from the regex or didn't match. */
internal fun MatchResult.named(name: String): String? =
    try {
        groups[name]?.value
    } catch (_: IllegalArgumentException) {
        null
    }
