package com.example.hisab

import com.example.hisab.model.AccountPeriod
import com.example.hisab.model.DashboardSummary
import com.example.hisab.model.Person
import com.example.hisab.model.Transaction
import com.example.hisab.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying balance calculation rules, signed amount arithmetic,
 * and settlement period rules specified in Section 9 & 12 of the requirements.
 */
class BalanceCalculationTest {

    @Test
    fun testSignedAmountConvention() {
        // Positive: Other person owes app user
        val theyOweMeMultiplier = TransactionType.THEY_OWE_ME.signMultiplier
        assertEquals(1, theyOweMeMultiplier)

        // Negative: App user owes other person
        val iOweThemMultiplier = TransactionType.I_OWE_THEM.signMultiplier
        assertEquals(-1, iOweThemMultiplier)

        // Payment received: Reduces what they owe me (-1)
        val paymentReceivedMultiplier = TransactionType.PAYMENT_RECEIVED.signMultiplier
        assertEquals(-1, paymentReceivedMultiplier)

        // Payment made: Reduces what I owe them (+1)
        val paymentMadeMultiplier = TransactionType.PAYMENT_MADE.signMultiplier
        assertEquals(1, paymentMadeMultiplier)
    }

    @Test
    fun testBalanceCalculation_scenarioFromSpec() {
        // Spec Section 9:
        // Transaction 1: +₹100 for money lent. Balance: +₹100.
        var balancePaise = 0L
        val tx1 = 10000L // +₹100
        balancePaise += tx1
        assertEquals(10000L, balancePaise)

        // Transaction 2: -₹30 for payment made on user's behalf. Balance: +₹70.
        val tx2 = -3000L // -₹30
        balancePaise += tx2
        assertEquals(7000L, balancePaise)

        // Transaction 3: -₹70 payment received from other person. Balance: ₹0.
        val tx3 = -7000L // -₹70
        balancePaise += tx3
        assertEquals(0L, balancePaise)
    }

    @Test
    fun testSettlementPeriodRules() {
        // Period 1 is active
        val period1 = AccountPeriod(
            id = 1L,
            personId = 10L,
            startedAt = 1000L,
            openingBalancePaise = 0L,
            status = AccountPeriod.STATUS_OPEN
        )
        assertTrue(period1.isOpen)

        val p1Transactions = listOf(
            Transaction(id = 1L, personId = 10L, periodId = 1L, amountPaise = 10000L, reason = "Lent for dinner"),
            Transaction(id = 2L, personId = 10L, periodId = 1L, amountPaise = 9000L, reason = "Movie ticket")
        )
        val finalBalanceP1 = period1.openingBalancePaise + p1Transactions.sumOf { it.amountPaise }
        assertEquals(19000L, finalBalanceP1) // +₹190.00

        // Settlement event occurs:
        // Period 1 is closed with its final balance stamped
        val closedPeriod1 = period1.copy(
            closedAt = 2000L,
            closingBalancePaise = finalBalanceP1,
            status = AccountPeriod.STATUS_CLOSED
        )
        assertFalse(closedPeriod1.isOpen)
        assertEquals(19000L, closedPeriod1.closingBalancePaise)

        // A brand new Period 2 is opened with 0 opening balance
        val period2 = AccountPeriod(
            id = 2L,
            personId = 10L,
            startedAt = 2000L,
            openingBalancePaise = 0L,
            status = AccountPeriod.STATUS_OPEN
        )
        assertTrue(period2.isOpen)

        // New transactions in Period 2 are independent of Period 1
        val p2Transactions = listOf(
            Transaction(id = 3L, personId = 10L, periodId = 2L, amountPaise = 5000L, reason = "New coffee")
        )
        val activeBalanceP2 = period2.openingBalancePaise + p2Transactions.sumOf { it.amountPaise }
        assertEquals(5000L, activeBalanceP2)

        // Period 1 transactions remain completely preserved for history without affecting current balance
        assertEquals(2, p1Transactions.size)
        assertEquals(19000L, p1Transactions.sumOf { it.amountPaise })
    }

    @Test
    fun testDashboardSummaryCalculation() {
        val people = listOf(
            Person(id = 1L, name = "Sakshi", currentBalancePaise = 19000L),    // +₹190 (they owe me)
            Person(id = 2L, name = "Janvi", currentBalancePaise = 17700L),     // +₹177 (they owe me)
            Person(id = 3L, name = "Prachi", currentBalancePaise = 100000L),   // +₹1,000 (they owe me)
            Person(id = 4L, name = "Prathamesh", currentBalancePaise = 8200L), // +₹82 (they owe me)
            Person(id = 5L, name = "Amit", currentBalancePaise = -25000L)      // -₹250 (I owe them)
        )

        var totalOthersOweMe = 0L
        var totalIOweOthers = 0L

        for (p in people) {
            if (p.currentBalancePaise > 0) {
                totalOthersOweMe += p.currentBalancePaise
            } else if (p.currentBalancePaise < 0) {
                totalIOweOthers += kotlin.math.abs(p.currentBalancePaise)
            }
        }

        val netBalance = totalOthersOweMe - totalIOweOthers

        val summary = DashboardSummary(
            totalOthersOweMe = totalOthersOweMe,
            totalIOweOthers = totalIOweOthers,
            netBalance = netBalance,
            activePeopleCount = people.size
        )

        assertEquals(144900L, summary.totalOthersOweMe) // 190 + 177 + 1000 + 82 = 1449
        assertEquals(25000L, summary.totalIOweOthers)   // 250
        assertEquals(119900L, summary.netBalance)       // 1449 - 250 = 1199
        assertEquals(5, summary.activePeopleCount)
    }
}
