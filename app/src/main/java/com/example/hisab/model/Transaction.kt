package com.example.hisab.model

/**
 * Represents an individual financial transaction between the user and another person.
 *
 * Consistent Signed-Amount Convention:
 * - Positive amount (> 0): The other person owes the app user.
 * - Negative amount (< 0): The app user owes the other person.
 * - Stored in integer paise (1 Rupee = 100 paise) to prevent floating-point precision errors.
 *
 * @property id Unique database record ID.
 * @property personId Foreign key referencing people(id).
 * @property periodId Foreign key referencing account_periods(id).
 * @property amountPaise Signed amount in integer paise.
 * @property reason Short summary / purpose (e.g. "Dinner at cafe", "Cab fare").
 * @property transactionDate Timestamp when the transaction actually occurred.
 * @property createdAt Timestamp when this entry was created in the app.
 * @property updatedAt Timestamp of last update.
 * @property notes Optional supplementary notes or details.
 */
data class Transaction(
    val id: Long = 0,
    val personId: Long,
    val periodId: Long,
    val amountPaise: Long,
    val reason: String,
    val transactionDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val remoteId: String? = null
)
