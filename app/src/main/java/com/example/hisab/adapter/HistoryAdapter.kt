package com.example.hisab.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hisab.R
import com.example.hisab.databinding.ItemHistoryPeriodBinding
import com.example.hisab.model.AccountPeriod
import com.example.hisab.model.PeriodHistoryItem
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateFormatter

/**
 * Adapter for rendering the full accounting period history for a person.
 *
 * Shows both active and closed (settled) periods, including settlement timestamps,
 * final balances, and all associated transactions.
 */
class HistoryAdapter(
    private var historyItems: List<PeriodHistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    fun updateList(newList: List<PeriodHistoryItem>) {
        historyItems = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryPeriodBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyItems[position])
    }

    override fun getItemCount(): Int = historyItems.size

    inner class HistoryViewHolder(private val binding: ItemHistoryPeriodBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PeriodHistoryItem) {
            val context = binding.root.context
            val isCurrent = item.period.status == AccountPeriod.STATUS_OPEN

            // 1. Period Title and Status Badge
            if (isCurrent) {
                binding.tvPeriodTitle.text = context.getString(R.string.period_current)
                binding.tvPeriodStatusBadge.text = "ACTIVE"
                binding.tvPeriodStatusBadge.setBackgroundResource(R.drawable.bg_badge_positive)
                binding.tvPeriodStatusBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.positive_balance)
                )
                binding.layoutSettlementDetails.visibility = View.GONE
            } else {
                binding.tvPeriodTitle.text = "Settled Accounting Period"
                binding.tvPeriodStatusBadge.text = "SETTLED"
                binding.tvPeriodStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
                binding.tvPeriodStatusBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.settled_balance)
                )

                // Settlement details
                binding.layoutSettlementDetails.visibility = View.VISIBLE
                item.settlement?.let { settlement ->
                    binding.tvSettledAt.text = "Settled on ${DateFormatter.formatDateTime(settlement.settledAt)}"
                    binding.tvFinalBalance.text = "Settled Balance: ${CurrencyFormatter.formatSignedRupees(settlement.finalBalancePaise)}"
                    if (!settlement.note.isNullOrBlank()) {
                        binding.tvSettlementNote.visibility = View.VISIBLE
                        binding.tvSettlementNote.text = "Note: ${settlement.note}"
                    } else {
                        binding.tvSettlementNote.visibility = View.GONE
                    }
                }
            }

            // 2. Net Period Total
            val balanceStr = CurrencyFormatter.formatSignedRupees(item.calculatedBalancePaise)
            binding.tvPeriodBalanceSummary.text = "Period Net Total: $balanceStr"

            // 3. Transactions Count and Items
            val txCount = item.transactions.size
            binding.tvTransactionsCount.text = "Transactions ($txCount)"

            binding.containerTransactions.removeAllViews()
            if (item.transactions.isEmpty()) {
                binding.tvNoTransactionsNotice.visibility = View.VISIBLE
            } else {
                binding.tvNoTransactionsNotice.visibility = View.GONE
                val inflater = LayoutInflater.from(context)

                for (tx in item.transactions) {
                    val rowView = inflater.inflate(R.layout.item_transaction, binding.containerTransactions, false)

                    val tvReason = rowView.findViewById<TextView>(R.id.tvReason)
                    val tvDate = rowView.findViewById<TextView>(R.id.tvDate)
                    val tvAmount = rowView.findViewById<TextView>(R.id.tvAmount)
                    val tvTypeTag = rowView.findViewById<TextView>(R.id.tvTypeTag)
                    val viewIndicator = rowView.findViewById<View>(R.id.viewIndicator)
                    val tvNotes = rowView.findViewById<TextView>(R.id.tvNotes)

                    tvReason.text = tx.reason
                    tvDate.text = DateFormatter.formatRelativeDate(tx.transactionDate)

                    if (!tx.notes.isNullOrBlank()) {
                        tvNotes.visibility = View.VISIBLE
                        tvNotes.text = "Note: ${tx.notes}"
                    } else {
                        tvNotes.visibility = View.GONE
                    }

                    tvAmount.text = CurrencyFormatter.formatSignedRupees(tx.amountPaise)
                    if (tx.amountPaise > 0) {
                        val posColor = ContextCompat.getColor(context, R.color.positive_balance)
                        tvAmount.setTextColor(posColor)
                        viewIndicator.setBackgroundColor(posColor)
                        tvTypeTag.text = "They owe me"
                        tvTypeTag.setTextColor(posColor)
                    } else {
                        val negColor = ContextCompat.getColor(context, R.color.negative_balance)
                        tvAmount.setTextColor(negColor)
                        viewIndicator.setBackgroundColor(negColor)
                        tvTypeTag.text = "I owe them"
                        tvTypeTag.setTextColor(negColor)
                    }

                    binding.containerTransactions.addView(rowView)
                }
            }
        }
    }
}
