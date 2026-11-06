package io.github.txnparse

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextTest {
    @Test
    fun amounts() {
        assertEquals(BigDecimal("123456.50"), Text.parseAmount("1,23,456.5"))
        assertEquals(BigDecimal("2500.00"), Text.parseAmount("2500."))
        assertEquals(BigDecimal("120.00"), Text.parseAmount("120.0"))
        assertNull(Text.parseAmount("abc"))
        assertNull(Text.parseAmount(""))
    }

    @Test
    fun senders() {
        assertEquals("HDFCBK", Text.normalizeSender("VM-HDFCBK"))
        assertEquals("HDFCBK", Text.normalizeSender("ad-hdfcbk-s"))
        assertEquals("ATMSBI", Text.normalizeSender("ATMSBI"))
    }

    @Test
    fun dates() {
        assertEquals(LocalDate.of(2026, 1, 8), Text.parseDate("08-JAN-26", listOf("dd-MMM-yy")))
        assertEquals(LocalDate.of(2026, 1, 11), Text.parseDate("11Jan26", listOf("dd/MM/yy", "ddMMMyy")))
        assertNull(Text.parseDate("31-02-26", listOf("dd-MM-yy")))
    }
}
