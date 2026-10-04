package com.example.hisab.data

import android.content.Context

/**
 * Utility for loading illustrative sample records for testing or portfolio demonstration.
 *
 * Populates:
 * - Sakshi: owes ₹190 (e.g. Lent ₹100, Lunch ₹90)
 * - Janvi: owes ₹177 (e.g. Metro card ₹100, Stationery ₹77)
 * - Prachi: owes ₹1,000 (e.g. Shared hostel supplies ₹1,000)
 * - Prathamesh: owes ₹82 (e.g. Coffee ₹100, payment received -₹18)
 */
object SampleDataLoader {

    fun loadSampleData(context: Context): Boolean {
        val personRepo = PersonRepository(context)
        val transRepo = TransactionRepository(context)

        val oneDayMs = 86400000L
        val now = System.currentTimeMillis()

        // 1. Sakshi (+₹190)
        val (sakshiId, _) = personRepo.addPerson("Sakshi")
        if (sakshiId > 0) {
            transRepo.addTransaction(
                personId = sakshiId,
                amountPaise = 10000L, // +₹100
                reason = "Money lent for books",
                transactionDate = now - (3 * oneDayMs)
            )
            transRepo.addTransaction(
                personId = sakshiId,
                amountPaise = 9000L, // +₹90
                reason = "Lunch bill split",
                transactionDate = now - (1 * oneDayMs)
            )
        }

        // 2. Janvi (+₹177)
        val (janviId, _) = personRepo.addPerson("Janvi")
        if (janviId > 0) {
            transRepo.addTransaction(
                personId = janviId,
                amountPaise = 10000L, // +₹100
                reason = "Metro recharge",
                transactionDate = now - (4 * oneDayMs)
            )
            transRepo.addTransaction(
                personId = janviId,
                amountPaise = 7700L, // +₹77
                reason = "Stationery printouts",
                transactionDate = now - (2 * oneDayMs)
            )
        }

        // 3. Prachi (+₹1,000)
        val (prachiId, _) = personRepo.addPerson("Prachi")
        if (prachiId > 0) {
            transRepo.addTransaction(
                personId = prachiId,
                amountPaise = 100000L, // +₹1,000
                reason = "Shared hostel groceries",
                transactionDate = now - (5 * oneDayMs)
            )
        }

        // 4. Prathamesh (+₹82)
        val (prathameshId, _) = personRepo.addPerson("Prathamesh")
        if (prathameshId > 0) {
            transRepo.addTransaction(
                personId = prathameshId,
                amountPaise = 10000L, // +₹100
                reason = "Evening cafe snack",
                transactionDate = now - (2 * oneDayMs)
            )
            transRepo.addTransaction(
                personId = prathameshId,
                amountPaise = -1800L, // -₹18 returned
                reason = "Returned change in cash",
                transactionDate = now - (1 * oneDayMs)
            )
        }

        return true
    }
}
