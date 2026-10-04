package com.example.hisab.model

/**
 * Represents a settlement event marking an accounting period as cleared.
 *
 * Recording this event documents that the user and the other person agreed
 * to clear the balance at this specific point in time. It preserves the final
 * balance and timestamp without destroying any transaction records.
 *
 * @property id Unique record ID.
 * @property personId Foreign key referencing people(id).
 * @property periodId Foreign key referencing the closed account_periods(id).
 * @property finalBalancePaise The signed balance at the moment of settlement.
 * @property settledAt Timestamp when the settlement occurred.
 * @property note Optional note describing the settlement (e.g. "Settled via UPI", "Cash paid").
 */
data class Settlement(
    val id: Long = 0,
    val personId: Long,
    val periodId: Long,
    val finalBalancePaise: Long,
    val settledAt: Long = System.currentTimeMillis(),
    val note: String? = null
)
