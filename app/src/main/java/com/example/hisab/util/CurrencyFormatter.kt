package com.example.hisab.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Beginner-Friendly Currency Utilities for Hisab.
 *
 * Why store money in paise?
 * Floating-point numbers (`Float` and `Double`) cannot accurately represent
 * base-10 decimals like 0.10 or 0.70 because computers represent floating-point
 * numbers in binary (base-2). Over time, repeated addition or subtraction causes
 * fractional cent errors (e.g. 0.1 + 0.2 = 0.30000000000000004).
 *
 * By storing amounts as integer paise (`Long`), every calculation is 100% exact!
 * 1 Rupee = 100 Paise.
 */
object CurrencyFormatter {

    /**
     * Formats an amount in paise into an Indian Rupee string with the ₹ symbol.
     * Examples:
     * - 10000L -> "₹100" (or "₹100.00")
     * - 10000000L -> "₹1,00,000"
     *
     * @param paise Amount in integer paise.
     * @param showDecimals If true, always displays two decimal places (e.g. ₹100.00).
     */
    fun formatRupees(paise: Long, showDecimals: Boolean = true): String {
        val absPaise = abs(paise)
        val rupees = absPaise / 100
        val remainderPaise = absPaise % 100

        val formattedIndianInt = formatIndianInteger(rupees)

        return if (showDecimals || remainderPaise > 0) {
            val decimalStr = String.format(Locale.US, "%02d", remainderPaise)
            "₹$formattedIndianInt.$decimalStr"
        } else {
            "₹$formattedIndianInt"
        }
    }

    /**
     * Formats an amount with an explicit plus or minus sign.
     * Examples:
     * - +19000L -> "+₹190.00"
     * - -5000L  -> "-₹50.00"
     * - 0L      -> "₹0.00"
     */
    fun formatSignedRupees(paise: Long, showDecimals: Boolean = true): String {
        val formatted = formatRupees(paise, showDecimals)
        return when {
            paise > 0 -> "+$formatted"
            paise < 0 -> "-$formatted"
            else -> formatted
        }
    }

    /**
     * Converts a user input string (e.g. "150", "150.5", "150.75") into integer paise.
     * Uses [BigDecimal] to prevent floating point inaccuracies.
     *
     * @return Positive amount in paise, or null if input is invalid or <= 0.
     */
    fun parseRupeesToPaise(input: String): Long? {
        val trimmed = input.trim().replace(",", "")
        if (trimmed.isEmpty()) return null

        return try {
            val decimal = BigDecimal(trimmed)
            if (decimal <= BigDecimal.ZERO) return null
            val paiseDecimal = decimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
            paiseDecimal.longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns a human-readable explanation of who owes whom based on the signed balance.
     *
     * Rules:
     * - Positive balance: The other person owes the user.
     * - Negative balance: The user owes the other person.
     * - Zero balance: All settled.
     */
    fun getBalanceExplanation(personName: String, balancePaise: Long): String {
        return when {
            balancePaise > 0 -> "$personName owes you ${formatRupees(balancePaise)}"
            balancePaise < 0 -> "You owe $personName ${formatRupees(balancePaise)}"
            else -> "All settled up (₹0)"
        }
    }

    /**
     * Formats an integer using the Indian numbering system:
     * e.g., 1000 -> "1,000"
     *       100000 -> "1,00,000"
     *       10000000 -> "1,00,00,000"
     */
    private fun formatIndianInteger(value: Long): String {
        if (value < 1000) return value.toString()

        val s = value.toString()
        val lastThree = s.substring(s.length - 3)
        var remaining = s.substring(0, s.length - 3)

        val parts = mutableListOf<String>()
        while (remaining.length > 2) {
            parts.add(0, remaining.substring(remaining.length - 2))
            remaining = remaining.substring(0, remaining.length - 2)
        }
        if (remaining.isNotEmpty()) {
            parts.add(0, remaining)
        }
        parts.add(lastThree)
        return parts.joinToString(",")
    }
}
