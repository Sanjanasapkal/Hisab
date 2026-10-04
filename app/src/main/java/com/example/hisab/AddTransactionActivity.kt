package com.example.hisab

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hisab.data.SyncManager
import com.example.hisab.data.TransactionRepository
import com.example.hisab.databinding.ActivityAddTransactionBinding
import com.example.hisab.model.TransactionType
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Screen 3: Form for adding a new financial transaction for a person.
 *
 * Provides real-time balance effect preview, date picking, and validation.
 */
class AddTransactionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERSON_ID = "extra_person_id"
        const val EXTRA_PERSON_NAME = "extra_person_name"
    }

    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var transactionRepository: TransactionRepository

    private var personId: Long = -1L
    private var personName: String = ""
    private var selectedDateTimestamp: Long = System.currentTimeMillis()
    private var isSubmitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.appBarLayout)

        personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)
        personName = intent.getStringExtra(EXTRA_PERSON_NAME).orEmpty()

        if (personId == -1L) {
            Toast.makeText(this, "Invalid person account", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        transactionRepository = TransactionRepository(this)

        setupUI()
        setupListeners()
        updateEffectPreview()
    }

    private fun setupUI() {
        binding.tvTransactionFor.text = getString(R.string.transaction_for, personName)
        binding.tvSelectedDate.text = DateFormatter.formatDateOnly(selectedDateTimestamp)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Date Picker
        binding.btnPickDate.setOnClickListener {
            showDatePicker()
        }

        // Live Preview on radio button changes
        binding.rgTransactionType.setOnCheckedChangeListener { _, _ ->
            updateEffectPreview()
        }

        // Live Preview on amount text change
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateEffectPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Save Button with double-submission prevention
        binding.btnSaveTransaction.setOnClickListener {
            if (!isSubmitting) {
                saveTransaction()
            }
        }
    }

    private fun getSelectedTransactionType(): TransactionType {
        return when (binding.rgTransactionType.checkedRadioButtonId) {
            R.id.rbIOweThem -> TransactionType.I_OWE_THEM
            R.id.rbPaymentReceived -> TransactionType.PAYMENT_RECEIVED
            R.id.rbPaymentMade -> TransactionType.PAYMENT_MADE
            else -> TransactionType.THEY_OWE_ME
        }
    }

    /**
     * Updates the live preview text and badge color reflecting the exact effect on the balance.
     */
    private fun updateEffectPreview() {
        val amountStr = binding.etAmount.text?.toString().orEmpty()
        val parsedPaise = CurrencyFormatter.parseRupeesToPaise(amountStr) ?: 0L
        val type = getSelectedTransactionType()

        val signedPaise = parsedPaise * type.signMultiplier

        if (parsedPaise == 0L) {
            binding.tvEffectPreview.text = "Enter an amount above to see the balance effect"
            binding.tvEffectPreview.setTextColor(ContextCompat.getColor(this, R.color.secondary_text))
            binding.layoutEffectPreview.setBackgroundResource(R.drawable.bg_badge_neutral)
            return
        }

        val formattedSigned = CurrencyFormatter.formatSignedRupees(signedPaise)
        val explanation = when (type) {
            TransactionType.THEY_OWE_ME -> "Effect: $formattedSigned ($personName will owe you ${CurrencyFormatter.formatRupees(parsedPaise)} more)"
            TransactionType.I_OWE_THEM -> "Effect: $formattedSigned (You will owe $personName ${CurrencyFormatter.formatRupees(parsedPaise)} more)"
            TransactionType.PAYMENT_RECEIVED -> "Effect: $formattedSigned (Reduces what $personName owes you)"
            TransactionType.PAYMENT_MADE -> "Effect: $formattedSigned (Reduces what you owe $personName)"
        }

        binding.tvEffectPreview.text = explanation

        if (signedPaise > 0) {
            binding.tvEffectPreview.setTextColor(ContextCompat.getColor(this, R.color.positive_balance))
            binding.layoutEffectPreview.setBackgroundResource(R.drawable.bg_badge_positive)
        } else {
            binding.tvEffectPreview.setTextColor(ContextCompat.getColor(this, R.color.negative_balance))
            binding.layoutEffectPreview.setBackgroundResource(R.drawable.bg_badge_negative)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateTimestamp = chosen.timeInMillis
                binding.tvSelectedDate.text = DateFormatter.formatDateOnly(selectedDateTimestamp)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.show()
    }

    private fun saveTransaction() {
        // 1. Validate Amount
        val amountStr = binding.etAmount.text?.toString().orEmpty()
        val parsedPaise = CurrencyFormatter.parseRupeesToPaise(amountStr)
        if (parsedPaise == null || parsedPaise <= 0L) {
            binding.tilAmount.error = getString(R.string.error_invalid_amount)
            return
        } else {
            binding.tilAmount.error = null
        }

        // 2. Validate Reason
        val reason = binding.etReason.text?.toString().orEmpty().trim()
        if (reason.isEmpty()) {
            binding.tilReason.error = getString(R.string.error_empty_reason)
            return
        } else {
            binding.tilReason.error = null
        }

        // 3. Prevent duplicate submission
        isSubmitting = true
        binding.btnSaveTransaction.isEnabled = false

        val type = getSelectedTransactionType()
        val signedAmountPaise = parsedPaise * type.signMultiplier
        val notes = binding.etNotes.text?.toString()

        val (_, error) = transactionRepository.addTransaction(
            personId = personId,
            amountPaise = signedAmountPaise,
            reason = reason,
            transactionDate = selectedDateTimestamp,
            notes = notes
        )

        if (error != null) {
            isSubmitting = false
            binding.btnSaveTransaction.isEnabled = true
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        } else {
            // Instantly sync with MongoDB Atlas in background
            CoroutineScope(Dispatchers.IO).launch {
                SyncManager.sync(applicationContext)
            }
            Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
