package io.github.txnparse

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate

/** Direction of money movement, from the account holder's point of view. */
@Serializable
public enum class TransactionType { DEBIT, CREDIT }

/** Payment rail, detected from keywords in the message. */
@Serializable
public enum class Channel { UPI, CARD, ATM, NEFT, IMPS, RTGS }

/** How the transaction was extracted. */
public enum class Confidence {
    /** Matched a bank-specific rule. Fields are reliable. */
    HIGH,

    /** Extracted by the generic keyword parser. Check before trusting. */
    LOW,
}

/** A money movement extracted from one bank SMS or notification. */
public data class Transaction(
    val type: TransactionType,
    val amount: BigDecimal,
    val currency: String,
    /** Payee for debits, payer for credits. */
    val merchant: String? = null,
    /** Last digits of the account or card, e.g. "1234". */
    val accountTail: String? = null,
    /** Available balance or available credit limit, when the message includes it. */
    val balance: BigDecimal? = null,
    /** Date printed in the message. Null when absent; fall back to the SMS timestamp. */
    val date: LocalDate? = null,
    /** UPI / RRN / bank reference number. */
    val reference: String? = null,
    val channel: Channel? = null,
    /** Id of the bank rule file that matched, e.g. "hdfc". Null for generic matches. */
    val bankId: String? = null,
    val confidence: Confidence,
)

/** Outcome of [TransactionParser.parse]. */
public sealed interface ParseResult {
    public data class Parsed(val transaction: Transaction) : ParseResult

    /** Message looks bank-related but moved no money (OTP, due reminder, failed payment...). */
    public data class Ignored(val reason: String) : ParseResult

    /** Nothing that looks like a transaction. */
    public data object NotATransaction : ParseResult
}
