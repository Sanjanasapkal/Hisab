package com.example.hisab.model

/**
 * Aggregated summary for the home screen dashboard.
 *
 * All values are calculated from the current unsettled open periods across all people.
 *
 * @property totalOthersOweMe Total positive balances (money people owe you).
 * @property totalIOweOthers Total negative balances expressed as positive magnitude (money you owe people).
 * @property netBalance Net financial position (totalOthersOweMe - totalIOweOthers).
 * @property activePeopleCount Total number of active people registered.
 */
data class DashboardSummary(
    val totalOthersOweMe: Long = 0L,
    val totalIOweOthers: Long = 0L,
    val netBalance: Long = 0L,
    val activePeopleCount: Int = 0
)
