package io.github.txnparse

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TransactionParserTest {
    private val parser = TransactionParser.withBundledRules()

    private fun parsed(body: String, sender: String? = null): Transaction =
        assertIs<ParseResult.Parsed>(parser.parse(body, sender)).transaction

    @Test
    fun `bank rule match is high confidence`() {
        val txn = parsed("Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 05/01/26 Ref 401234567890", "VM-HDFCBK")
        assertEquals(Confidence.HIGH, txn.confidence)
        assertEquals("hdfc", txn.bankId)
        assertEquals(BigDecimal("250.00"), txn.amount)
        assertEquals(LocalDate.of(2026, 1, 5), txn.date)
    }

    @Test
    fun `ignores OTPs, reminders, requests and failures`() {
        val cases = mapOf(
            "123456 is your OTP for a txn of Rs 500.00 at AMAZON on HDFC Bank Card 1234" to "otp",
            "Your credit card bill of Rs 8,450.00 is due on 20-Jan-26. Pay now." to "due",
            "rahul@okaxis has requested money of Rs 300 from your A/c. Approve in your UPI app." to "request",
            "Txn of Rs 999.00 on your card XX1234 has been declined." to "failed",
            "Rs 499.00 will be debited from A/c XX1234 on 25-Jan-26 for NETFLIX autopay." to "scheduled",
            "Reminder: INR 15000.00 outstanding on your Credit Card XX1234. Pay before 25-Nov-2026." to "due",
            "Your Credit Card limit increased to INR 200000.00. Available limit INR 185000.00." to "limit_change",
            "Hi, I debited INR 500 from my bank for your order. Please pay me." to "personal",
            "Payment of Rs.23456.00 has been received towards your HDFC Bank Credit Card XX9012 on 05-11-26. Thank you." to "card_bill_payment",
            "We have received payment of Rs.5,000.00 towards your SBI Credit Card ending 3456. Thank you." to "card_bill_payment",
        )
        cases.forEach { (sms, reason) ->
            assertEquals(ParseResult.Ignored(reason), parser.parse(sms), sms)
        }
    }

    @Test
    fun `credit card refunds are not mistaken for bill payments`() {
        val txn = parsed("Rs 500.00 credited to your HDFC Bank Credit Card XX9012 as refund from FLIPKART.")
        assertEquals(TransactionType.CREDIT, txn.type)
        assertEquals(BigDecimal("500.00"), txn.amount)
    }

    @Test
    fun `plain text is not a transaction`() {
        assertEquals(ParseResult.NotATransaction, parser.parse("Hey, are we still on for dinner?"))
        assertEquals(ParseResult.NotATransaction, parser.parse("   "))
        // Money but no banking word: promotional, not a transaction.
        assertEquals(ParseResult.NotATransaction, parser.parse("Flat Rs 200 off on your next order! Use code SAVE200"))
    }

    @Test
    fun `generic fallback handles unknown banks`() {
        val txn = parsed(
            "Your A/c XX9876 is debited with Rs.1,250.50 towards GROFERS INDIA on 03-Feb-26. Avl Bal: Rs.10,000.00. UPI Ref No 512345678901",
            "VM-NEWBNK",
        )
        assertEquals(Confidence.LOW, txn.confidence)
        assertEquals(TransactionType.DEBIT, txn.type)
        assertEquals(BigDecimal("1250.50"), txn.amount)
        assertEquals(BigDecimal("10000.00"), txn.balance)
        assertEquals("9876", txn.accountTail)
        assertEquals("GROFERS INDIA", txn.merchant)
        assertEquals("512345678901", txn.reference)
        assertEquals(Channel.UPI, txn.channel)
        assertNull(txn.bankId)
    }

    @Test
    fun `generic fallback picks amount, not balance, when balance comes first`() {
        val txn = parsed("Avl Bal Rs 5,000.00. Your account 4455 credited with INR 750 from ACME CORP.")
        assertEquals(TransactionType.CREDIT, txn.type)
        assertEquals(BigDecimal("750.00"), txn.amount)
        assertEquals(BigDecimal("5000.00"), txn.balance)
    }

    @Test
    fun `generic fallback uses the first verb for type`() {
        // "debited ... credited" — money left this account.
        val txn = parsed("Acct XX111 debited for Rs 60.00; MERCHANT credited. Ref 998877665544")
        assertEquals(TransactionType.DEBIT, txn.type)
    }

    @Test
    fun `fallback can be disabled`() {
        val strict = TransactionParser.withBundledRules(useGenericFallback = false)
        assertEquals(ParseResult.NotATransaction, strict.parse("Your A/c XX9876 is debited with Rs.100 at SHOP."))
    }

    @Test
    fun `custom rules take priority over bundled rules`() {
        val custom = RuleLoader.fromJson(
            """
            {
              "id": "hdfc_custom", "name": "HDFC (custom)", "senders": ["HDFCBK"],
              "patterns": [{ "type": "CREDIT", "regex": "Sent Rs\\.?(?<amount>[\\d.,]+)" }]
            }
            """.trimIndent(),
        )
        val p = TransactionParser.withBundledRules(extraRules = listOf(custom))
        val txn = p.parseOrNull("Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 05/01/26 Ref 1", "HDFCBK")!!
        assertEquals("hdfc_custom", txn.bankId)
    }

    @Test
    fun `invalid rules are rejected with a clear message`() {
        val noAmount = assertThrows<IllegalArgumentException> {
            RuleLoader.fromJson("""{"id":"x","name":"X","senders":["X"],"patterns":[{"type":"DEBIT","regex":"Rs (\\d+)"}]}""")
        }
        assertEquals("Rule 'x' pattern 0 has no (?<amount>...) group", noAmount.message)

        assertThrows<IllegalArgumentException> {
            RuleLoader.fromJson("""{"id":"x","name":"X","senders":["X"],"patterns":[{"type":"DEBIT","regex":"(?<amount>\\d+) on (?<date>\\S+)"}]}""")
        }
    }
}
