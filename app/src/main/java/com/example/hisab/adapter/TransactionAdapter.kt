package com.example.hisab.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hisab.R
import com.example.hisab.databinding.ItemTransactionBinding
import com.example.hisab.model.Transaction
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateFormatter

/**
 * Adapter for rendering transactions in a list.
 * Supports an optional long-click action for deleting or managing an entry.
 */
class TransactionAdapter(
    private var transactions: List<Transaction>,
    private val onTransactionLongClicked: ((Transaction) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    fun updateList(newList: List<Transaction>) {
        transactions = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            val context = binding.root.context

            // Reason
            binding.tvReason.text = transaction.reason

            // Date & Time
            binding.tvDate.text = DateFormatter.formatRelativeDate(transaction.transactionDate)

            // Optional Notes
            if (!transaction.notes.isNullOrBlank()) {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = "Note: ${transaction.notes}"
            } else {
                binding.tvNotes.visibility = View.GONE
            }

            // Signed Amount & Colors
            val amount = transaction.amountPaise
            binding.tvAmount.text = CurrencyFormatter.formatSignedRupees(amount)

            if (amount > 0) {
                // They owe me / positive (Outgoing money / You Gave)
                val positiveColor = ContextCompat.getColor(context, R.color.positive_balance)
                binding.tvAmount.setTextColor(positiveColor)
                binding.viewIndicator.setBackgroundColor(positiveColor)
                binding.tvTypeTag.text = "↗ You Gave"
                binding.tvTypeTag.setTextColor(positiveColor)
            } else {
                // I owe them / negative (Incoming money / You Got)
                val negativeColor = ContextCompat.getColor(context, R.color.negative_balance)
                binding.tvAmount.setTextColor(negativeColor)
                binding.viewIndicator.setBackgroundColor(negativeColor)
                binding.tvTypeTag.text = "↙ You Got"
                binding.tvTypeTag.setTextColor(negativeColor)
            }

            // Long click listener for deletion confirmation
            onTransactionLongClicked?.let { callback ->
                binding.root.setOnLongClickListener {
                    callback(transaction)
                    true
                }
            }
        }
    }
}
