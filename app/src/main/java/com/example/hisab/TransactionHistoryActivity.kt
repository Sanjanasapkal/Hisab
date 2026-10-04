package com.example.hisab

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hisab.adapter.HistoryAdapter
import com.example.hisab.data.TransactionRepository
import com.example.hisab.databinding.ActivityTransactionHistoryBinding

/**
 * Screen 4: Transaction History screen.
 *
 * Displays the complete audit trail for a person across all accounting periods.
 * Demonstrates how settled accounts preserve earlier transactions.
 */
class TransactionHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERSON_ID = "extra_person_id"
        const val EXTRA_PERSON_NAME = "extra_person_name"
    }

    private lateinit var binding: ActivityTransactionHistoryBinding
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var historyAdapter: HistoryAdapter

    private var personId: Long = -1L
    private var personName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.appBarLayout)

        personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)
        personName = intent.getStringExtra(EXTRA_PERSON_NAME).orEmpty()

        if (personId == -1L) {
            Toast.makeText(this, "Person not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        transactionRepository = TransactionRepository(this)

        setupUI()
        loadHistory()
    }

    private fun setupUI() {
        binding.tvHistoryPersonSubtitle.text = "All accounting periods for $personName"
        binding.btnBack.setOnClickListener { finish() }

        historyAdapter = HistoryAdapter(emptyList())
        binding.rvHistoryPeriods.apply {
            layoutManager = LinearLayoutManager(this@TransactionHistoryActivity)
            adapter = historyAdapter
        }
    }

    private fun loadHistory() {
        val historyItems = transactionRepository.getPersonHistory(personId)
        val hasAnyTransactions = historyItems.any { it.transactions.isNotEmpty() }

        if (!hasAnyTransactions && historyItems.all { it.period.isOpen }) {
            binding.layoutEmptyHistory.visibility = View.VISIBLE
            binding.rvHistoryPeriods.visibility = View.GONE
        } else {
            binding.layoutEmptyHistory.visibility = View.GONE
            binding.rvHistoryPeriods.visibility = View.VISIBLE
            historyAdapter.updateList(historyItems)
        }
    }
}
