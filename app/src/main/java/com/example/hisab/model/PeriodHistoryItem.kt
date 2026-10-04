package com.example.hisab.model

/**
 * Encapsulates a full accounting period (either active or settled)
 * along with its associated settlement metadata and transactions list.
 */
data class PeriodHistoryItem(
    val period: AccountPeriod,
    val settlement: Settlement? = null,
    val transactions: List<Transaction> = emptyList(),
    val calculatedBalancePaise: Long = 0L
)
