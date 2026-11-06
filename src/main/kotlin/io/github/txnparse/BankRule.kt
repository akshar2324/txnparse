package io.github.txnparse

import kotlinx.serialization.Serializable

/**
 * One bank's message formats. Bundled rules live in
 * `src/main/resources/txnparse/rules/<id>.json`; see CONTRIBUTING.md.
 *
 * Each pattern is a regex with named groups. Only `amount` is required:
 * `amount`, `merchant`, `account`, `balance`, `date`, `ref`.
 */
@Serializable
public data class BankRule(
    val id: String,
    val name: String,
    val country: String = "IN",
    val currency: String = "INR",
    /** Sender ID fragments, e.g. "HDFCBK" matches "VM-HDFCBK" and "AD-HDFCBK-S". */
    val senders: List<String>,
    /** java.time patterns tried for the `date` group, e.g. "dd-MM-yy". */
    val dateFormats: List<String> = emptyList(),
    /** True once the formats were checked against real (redacted) messages. */
    val verified: Boolean = false,
    val patterns: List<PatternRule>,
    /** Example messages with expected output. Every sample is run by the test suite. */
    val samples: List<RuleSample> = emptyList(),
)

@Serializable
public data class PatternRule(
    val type: TransactionType,
    val regex: String,
    /** Overrides [BankRule.dateFormats] for this pattern. */
    val dateFormats: List<String> = emptyList(),
)

@Serializable
public data class RuleSample(
    val sms: String,
    val sender: String? = null,
    val expected: ExpectedFields,
)

/** Fields a sample must produce. Omitted fields are not checked. */
@Serializable
public data class ExpectedFields(
    val type: TransactionType,
    val amount: String,
    val merchant: String? = null,
    val account: String? = null,
    val balance: String? = null,
    /** ISO date, e.g. "2026-01-05". */
    val date: String? = null,
    val ref: String? = null,
    val channel: Channel? = null,
)
