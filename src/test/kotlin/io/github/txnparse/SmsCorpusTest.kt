package io.github.txnparse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the parser over `corpus/bank-sms-samples.json`: synthetic and anonymized bank SMS with
 * expected outcomes. The corpus covers more formats than the bundled rules, so this test does not
 * require every message to parse. It requires that nothing is booked wrongly:
 *
 * - messages classed `ignore` must never parse as a transaction;
 * - a `debit` or `credit` message that does parse must have the right direction and amount,
 *   and the right account tail when both sides have one;
 * - coverage of `debit` and `credit` messages must not drop below the recorded floor.
 */
class SmsCorpusTest {

    private data class Case(val id: String, val sender: String?, val sms: String, val expected: JsonObject) {
        val classification get() = expected.string("classification")
    }

    private val cases: List<Case> by lazy {
        val stream = javaClass.getResourceAsStream("/corpus/bank-sms-samples.json")
            ?: fail("corpus/bank-sms-samples.json missing from test resources")
        val root = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        root.getValue("cases").jsonArray.map { element ->
            val o = element.jsonObject
            Case(o.string("id")!!, o.string("sender"), o.string("sms")!!, o.getValue("expected").jsonObject)
        }
    }

    private val parser = TransactionParser.withBundledRules()

    @Test
    fun `corpus loads with unique ids`() {
        assertTrue(cases.size >= 180, "corpus has only ${cases.size} cases")
        val dupes = cases.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(dupes.isEmpty(), "duplicate case ids: $dupes")
    }

    @Test
    fun `ignored messages never become transactions`() {
        val wrong = cases.filter { it.classification == "ignore" }
            .filter { parser.parse(it.sms, it.sender) is ParseResult.Parsed }
            .map { it.id }
        assertTrue(wrong.isEmpty(), "booked messages that must be ignored: $wrong")
    }

    @Test
    fun `parsed debits and credits match the expected values`() {
        val problems = mutableListOf<String>()
        directional().forEach { case ->
            val txn = parser.parseOrNull(case.sms, case.sender) ?: return@forEach
            val wantType = if (case.classification == "debit") TransactionType.DEBIT else TransactionType.CREDIT
            if (txn.type != wantType) problems += "${case.id}: type ${txn.type}, expected $wantType"
            val wantAmount = case.expected.string("amount")?.let(::BigDecimal)
            if (wantAmount != null && txn.amount.compareTo(wantAmount) != 0) {
                problems += "${case.id}: amount ${txn.amount}, expected $wantAmount"
            }
            val wantTail = case.expected.string("account_last4")
            val gotTail = txn.accountTail
            if (wantTail != null && gotTail != null && !wantTail.endsWith(gotTail) && !gotTail.endsWith(wantTail)) {
                problems += "${case.id}: account $gotTail, expected $wantTail"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `coverage of debits and credits does not regress`() {
        val all = directional()
        val parsed = all.count { parser.parseOrNull(it.sms, it.sender) != null }
        println("SMS corpus: parsed $parsed of ${all.size} debit/credit messages")
        assertTrue(parsed >= COVERAGE_FLOOR, "parsed $parsed of ${all.size}; floor is $COVERAGE_FLOOR. Raise the floor when coverage improves.")
    }

    private fun directional() = cases.filter { it.classification == "debit" || it.classification == "credit" }

    private companion object {
        const val COVERAGE_FLOOR = 132
    }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        ?: get(key)?.jsonPrimitive?.content?.takeIf { it != "null" }
