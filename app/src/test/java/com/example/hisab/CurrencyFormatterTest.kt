package com.example.hisab

import com.example.hisab.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for CurrencyFormatter.
 * Verifies paise precision, Indian number grouping, and signed balance formatting.
 */
class CurrencyFormatterTest {

    @Test
    fun testFormatRupees_basicValues() {
        assertEquals("₹100.00", CurrencyFormatter.formatRupees(10000L))
        assertEquals("₹0.00", CurrencyFormatter.formatRupees(0L))
        assertEquals("₹190.50", CurrencyFormatter.formatRupees(19050L))
        assertEquals("₹0.75", CurrencyFormatter.formatRupees(75L))
    }

    @Test
    fun testFormatRupees_indianNumberSystemGrouping() {
        // Thousands: 1,000
        assertEquals("₹1,000.00", CurrencyFormatter.formatRupees(100000L))
        // Lakhs: 1,00,000
        assertEquals("₹1,00,000.00", CurrencyFormatter.formatRupees(10000000L))
        // Crores: 1,00,00,000
        assertEquals("₹1,00,00,000.00", CurrencyFormatter.formatRupees(1000000000L))
    }

    @Test
    fun testFormatSignedRupees() {
        assertEquals("+₹190.00", CurrencyFormatter.formatSignedRupees(19000L))
        assertEquals("-₹50.00", CurrencyFormatter.formatSignedRupees(-5000L))
        assertEquals("₹0.00", CurrencyFormatter.formatSignedRupees(0L))
    }

    @Test
    fun testParseRupeesToPaise_validInputs() {
        assertEquals(10000L, CurrencyFormatter.parseRupeesToPaise("100"))
        assertEquals(10000L, CurrencyFormatter.parseRupeesToPaise("100.00"))
        assertEquals(19050L, CurrencyFormatter.parseRupeesToPaise("190.50"))
        assertEquals(75L, CurrencyFormatter.parseRupeesToPaise("0.75"))
        assertEquals(100000L, CurrencyFormatter.parseRupeesToPaise("1,000"))
    }

    @Test
    fun testParseRupeesToPaise_invalidInputs() {
        assertNull(CurrencyFormatter.parseRupeesToPaise(""))
        assertNull(CurrencyFormatter.parseRupeesToPaise("   "))
        assertNull(CurrencyFormatter.parseRupeesToPaise("0"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("-50"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("abc"))
    }

    @Test
    fun testGetBalanceExplanation() {
        assertEquals("Sakshi owes you ₹190.00", CurrencyFormatter.getBalanceExplanation("Sakshi", 19000L))
        assertEquals("You owe Sakshi ₹50.00", CurrencyFormatter.getBalanceExplanation("Sakshi", -5000L))
        assertEquals("All settled up (₹0)", CurrencyFormatter.getBalanceExplanation("Sakshi", 0L))
    }
}
