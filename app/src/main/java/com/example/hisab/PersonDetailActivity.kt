package com.example.hisab

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hisab.adapter.TransactionAdapter
import com.example.hisab.api.ApiClient
import com.example.hisab.api.CreatePersonRequest
import com.example.hisab.api.SettleRequest
import com.example.hisab.data.PersonRepository
import com.example.hisab.data.SyncManager
import com.example.hisab.data.TransactionRepository
import com.example.hisab.databinding.ActivityPersonDetailBinding
import com.example.hisab.databinding.DialogAddPersonBinding
import com.example.hisab.databinding.DialogSettleHisabBinding
import com.example.hisab.model.Person
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Screen 2: Dedicated account details screen for a specific person.
 *
 * Displays the current unsettled balance, explanation of who owes whom,
 * current period transactions, and actions to add transactions, settle the account,
 * or inspect full historical periods.
 */
class PersonDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERSON_ID = "extra_person_id"
    }

    private lateinit var binding: ActivityPersonDetailBinding
    private lateinit var personRepository: PersonRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transactionAdapter: TransactionAdapter

    private var personId: Long = -1L
    private var currentPerson: Person? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.appBarLayout)

        personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)
        if (personId == -1L) {
            Toast.makeText(this, "Person not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        personRepository = PersonRepository(this)
        transactionRepository = TransactionRepository(this)

        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadAccountData()
        // Ensure changes are synced with cloud
        CoroutineScope(Dispatchers.IO).launch {
            SyncManager.sync(applicationContext)
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(
            transactions = emptyList(),
            onTransactionLongClicked = { transaction ->
                showDeleteTransactionDialog(transaction.id, transaction.reason)
            }
        )

        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(this@PersonDetailActivity)
            adapter = transactionAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Edit Person Name
        binding.btnEditPerson.setOnClickListener { showEditPersonDialog() }

        // Delete Person
        binding.btnDeletePerson.setOnClickListener { showDeletePersonConfirmation() }

        // Add Transaction
        binding.btnAddTransaction.setOnClickListener {
            val intent = Intent(this, AddTransactionActivity::class.java).apply {
                putExtra(AddTransactionActivity.EXTRA_PERSON_ID, personId)
                putExtra(AddTransactionActivity.EXTRA_PERSON_NAME, currentPerson?.name ?: "")
            }
            startActivity(intent)
        }

        // Settle Hisab
        binding.btnSettleHisab.setOnClickListener {
            showSettleConfirmationDialog()
        }

        // View History
        binding.btnViewHistory.setOnClickListener {
            val intent = Intent(this, TransactionHistoryActivity::class.java).apply {
                putExtra(TransactionHistoryActivity.EXTRA_PERSON_ID, personId)
                putExtra(TransactionHistoryActivity.EXTRA_PERSON_NAME, currentPerson?.name ?: "")
            }
            startActivity(intent)
        }
    }

    /**
     * Loads the latest person balance and current period transactions.
     * Automatically moves any balancing transactions summing to ₹0 into History.
     */
    private fun loadAccountData() {
        // Auto-settle check: If transactions in the open period net to ₹0.00, move them to history!
        transactionRepository.checkAndAutoSettleZeroBalance(personId)

        val person = personRepository.getPersonById(personId)
        if (person == null) {
            finish()
            return
        }
        currentPerson = person

        // 1. Header Name
        binding.tvHeaderPersonName.text = person.name

        // 2. Balance Amount and Styling
        val balance = person.currentBalancePaise
        binding.tvCurrentBalanceAmount.text = CurrencyFormatter.formatSignedRupees(balance)

        val explanation = CurrencyFormatter.getBalanceExplanation(person.name, balance)
        binding.tvBalanceExplanationBadge.text = explanation

        when {
            balance > 0 -> {
                binding.tvCurrentBalanceAmount.setTextColor(
                    ContextCompat.getColor(this, R.color.positive_balance)
                )
                binding.tvBalanceExplanationBadge.setBackgroundResource(R.drawable.bg_badge_positive)
                binding.tvBalanceExplanationBadge.setTextColor(
                    ContextCompat.getColor(this, R.color.positive_balance)
                )
            }
            balance < 0 -> {
                binding.tvCurrentBalanceAmount.setTextColor(
                    ContextCompat.getColor(this, R.color.negative_balance)
                )
                binding.tvBalanceExplanationBadge.setBackgroundResource(R.drawable.bg_badge_negative)
                binding.tvBalanceExplanationBadge.setTextColor(
                    ContextCompat.getColor(this, R.color.negative_balance)
                )
            }
            else -> {
                binding.tvCurrentBalanceAmount.setTextColor(
                    ContextCompat.getColor(this, R.color.secondary_text)
                )
                binding.tvBalanceExplanationBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
                binding.tvBalanceExplanationBadge.setTextColor(
                    ContextCompat.getColor(this, R.color.settled_balance)
                )
            }
        }

        // 3. Transactions List for Current Open Period
        val transactions = transactionRepository.getOpenPeriodTransactions(personId)
        transactionAdapter.updateList(transactions)

        binding.tvTransactionCount.text = "${transactions.size} entries"

        if (transactions.isEmpty()) {
            binding.layoutEmptyTransactions.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
        } else {
            binding.layoutEmptyTransactions.visibility = View.GONE
            binding.rvTransactions.visibility = View.VISIBLE
        }
    }

    /**
     * Shows dialog to confirm settling the account.
     */
    private fun showSettleConfirmationDialog() {
        val person = currentPerson ?: return
        val balance = person.currentBalancePaise

        val dialogBinding = DialogSettleHisabBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Populate current balance in dialog
        dialogBinding.tvSettleBalanceAmount.text = CurrencyFormatter.formatSignedRupees(balance)
        dialogBinding.tvSettleExplanation.text = CurrencyFormatter.getBalanceExplanation(person.name, balance)

        if (balance < 0) {
            dialogBinding.layoutSettleBalance.setBackgroundResource(R.drawable.bg_badge_negative)
            dialogBinding.tvSettleBalanceAmount.setTextColor(
                ContextCompat.getColor(this, R.color.negative_balance)
            )
        } else if (balance == 0L) {
            dialogBinding.layoutSettleBalance.setBackgroundResource(R.drawable.bg_badge_neutral)
            dialogBinding.tvSettleBalanceAmount.setTextColor(
                ContextCompat.getColor(this, R.color.secondary_text)
            )
        }

        // Default settlement date to today
        var selectedSettlementTimestamp = System.currentTimeMillis()
        dialogBinding.tvSelectedSettlementDate.text = DateFormatter.formatDateOnly(selectedSettlementTimestamp)

        dialogBinding.btnSelectSettlementDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedSettlementTimestamp }
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val chosen = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }
                    selectedSettlementTimestamp = chosen.timeInMillis
                    dialogBinding.tvSelectedSettlementDate.text = DateFormatter.formatDateOnly(selectedSettlementTimestamp)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialogBinding.btnCancelSettle.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmSettle.setOnClickListener {
            val note = dialogBinding.etSettlementNote.text?.toString()
            val (success, error) = transactionRepository.settleHisab(
                personId,
                note,
                settledAt = selectedSettlementTimestamp
            )
            if (success) {
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.settled_success), Toast.LENGTH_SHORT).show()
                loadAccountData()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. Sync pending local items first to ensure Atlas has all latest transactions
                        SyncManager.sync(applicationContext)

                        // 2. Fetch the person's remote ID
                        val remotePersonId = currentPerson?.remoteId
                            ?: personRepository.getPersonById(personId)?.remoteId

                        if (!remotePersonId.isNullOrBlank()) {
                            val settleReq = SettleRequest(
                                personId = remotePersonId,
                                note = note,
                                clientLocalId = personId,
                                settledAt = selectedSettlementTimestamp
                            )
                            ApiClient.getService(applicationContext).settleHisab(settleReq)

                            // 3. Re-sync to align periods
                            SyncManager.sync(applicationContext)
                        }
                    } catch (e: Exception) {
                        // Offline or network error
                    }
                }
            } else {
                Toast.makeText(this, error ?: "Settlement failed", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }

    /**
     * Dialog to edit person's name.
     */
    private fun showEditPersonDialog() {
        val person = currentPerson ?: return
        val dialogBinding = DialogAddPersonBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvDialogTitle.text = "Edit Person's Name"
        dialogBinding.etPersonName.setText(person.name)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSavePerson.setOnClickListener {
            val newName = dialogBinding.etPersonName.text?.toString().orEmpty()
            val (success, error) = personRepository.updatePersonName(person.id, newName)
            if (success) {
                dialog.dismiss()
                Toast.makeText(this, "Updated name to \"$newName\"", Toast.LENGTH_SHORT).show()
                loadAccountData()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val remoteId = person.remoteId ?: personRepository.getPersonById(person.id)?.remoteId
                        if (!remoteId.isNullOrBlank()) {
                            ApiClient.getService(applicationContext).updatePerson(
                                remoteId,
                                CreatePersonRequest(name = newName)
                            )
                        }
                        SyncManager.sync(applicationContext)
                    } catch (e: Exception) {
                        // Offline or network error
                    }
                }
            } else {
                dialogBinding.tilPersonName.error = error
            }
        }

        dialog.show()
    }

    /**
     * Confirmation to delete person.
     */
    private fun showDeletePersonConfirmation() {
        val person = currentPerson ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete ${person.name}?")
            .setMessage("Are you sure you want to delete ${person.name}? All active and historical transactions for this person will be removed.")
            .setPositiveButton("Delete") { _, _ ->
                val personIdToDelete = person.id
                val remoteId = person.remoteId ?: personRepository.getPersonById(personIdToDelete)?.remoteId

                // 1. Delete locally from SQLite
                personRepository.deletePerson(personIdToDelete)
                Toast.makeText(this, "Deleted ${person.name}", Toast.LENGTH_SHORT).show()

                // 2. Delete / archive on MongoDB Atlas
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (!remoteId.isNullOrBlank()) {
                            ApiClient.getService(applicationContext).deletePerson(remoteId)
                        }
                        SyncManager.sync(applicationContext)
                    } catch (e: Exception) {
                        // Offline or network error
                    }
                }

                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Confirmation to delete an individual transaction.
     */
    private fun showDeleteTransactionDialog(transactionId: Long, reason: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction?")
            .setMessage("Are you sure you want to delete \"$reason\"? The current balance will be recalculated.")
            .setPositiveButton("Delete") { _, _ ->
                val tx = transactionRepository.getTransactionById(transactionId)
                val remoteId = tx?.remoteId
                val deleted = transactionRepository.deleteTransaction(transactionId)
                if (deleted) {
                    Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
                    loadAccountData()

                    // Delete from MongoDB Atlas and trigger background sync
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            if (!remoteId.isNullOrBlank()) {
                                ApiClient.getService(applicationContext).deleteTransaction(remoteId)
                            }
                            SyncManager.sync(applicationContext)
                        } catch (e: Exception) {
                            // Offline or network error
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
