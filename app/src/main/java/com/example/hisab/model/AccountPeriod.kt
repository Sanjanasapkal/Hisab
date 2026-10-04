package com.example.hisab.model

/**
 * Represents an accounting period for a person.
 *
 * All active transactions belong to the person's current "open" period.
 * When the account is settled ("Settle Hisab"), this period is closed with its
 * final balance, and a fresh open period is created with an opening balance of 0.
 *
 * This design ensures previous transactions are never deleted, preserving the
 * complete historical audit trail while resetting the active balance cleanly.
 */
data class AccountPeriod(
    val id: Long = 0,
    val personId: Long,
    val startedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val openingBalancePaise: Long = 0L,
    val closingBalancePaise: Long? = null,
    val status: String = STATUS_OPEN
) {
    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_CLOSED = "closed"
    }

    val isOpen: Boolean get() = status == STATUS_OPEN
}
