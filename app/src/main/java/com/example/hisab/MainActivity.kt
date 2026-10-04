package com.example.hisab

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hisab.adapter.PersonAdapter
import com.example.hisab.api.ApiClient
import com.example.hisab.api.SessionManager
import com.example.hisab.auth.LoginActivity
import com.example.hisab.data.PersonRepository
import com.example.hisab.data.SyncManager
import com.example.hisab.databinding.ActivityMainBinding
import com.example.hisab.databinding.DialogAddPersonBinding
import com.example.hisab.model.Person
import com.example.hisab.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Dashboard Activity for Hisab.
 *
 * Displays:
 * - Financial summary overview (You will get, You will give, Net balance).
 * - Searchable list of people with live balance updates.
 * - Add person flow with duplicate & validation handling.
 * - Sample data loader for testing/portfolio presentation.
 * - Multi-user cloud synchronization with MongoDB Atlas.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var personRepository: PersonRepository
    private lateinit var personAdapter: PersonAdapter
    private lateinit var sessionManager: SessionManager

    private var allPeople: List<Person> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager.getInstance(this)

        // Session Gatekeeper: If user is not authenticated, redirect to Login
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.layoutHeader)

        personRepository = PersonRepository(this)

        setupRecyclerView()
        setupSearch()
        setupListeners()

        // Automatically trigger cloud sync in background upon launch
        triggerSilentSync()
    }

    override fun onResume() {
        super.onResume()
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin()
            return
        }

        updateUserGreeting()
        loadDashboardData()
        triggerSilentSync()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun updateUserGreeting() {
        val displayName = sessionManager.getUserDisplayName()
        if (displayName.isNotBlank() && displayName != "User") {
            binding.tvAppSubtitle.text = "Logged in as $displayName"
        } else {
            binding.tvAppSubtitle.text = getString(R.string.app_tagline)
        }
    }

    private fun setupRecyclerView() {
        personAdapter = PersonAdapter(emptyList()) { person ->
            val intent = Intent(this, PersonDetailActivity::class.java).apply {
                putExtra(PersonDetailActivity.EXTRA_PERSON_ID, person.id)
            }
            startActivity(intent)
        }

        binding.rvPeople.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = personAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearchPeople.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterPeople(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupListeners() {
        // Add Person buttons
        binding.fabAddPerson.setOnClickListener { showAddPersonDialog() }
        binding.btnAddPersonHeader.setOnClickListener { showAddPersonDialog() }
        binding.btnEmptyAddPerson.setOnClickListener { showAddPersonDialog() }

        // Menu button (Cloud sync, sample data loader, about dialog, logout)
        binding.btnMenu.setOnClickListener { showOptionsMenuDialog() }
    }

    /**
     * Loads the latest people list and updates the summary card metrics.
     */
    private fun loadDashboardData() {
        allPeople = personRepository.getAllPeople()
        val summary = personRepository.getDashboardSummary()

        // 1. Update Summary Metrics
        binding.tvTotalOthersOweMe.text = CurrencyFormatter.formatSignedRupees(summary.totalOthersOweMe)
        binding.tvTotalIOweOthers.text = CurrencyFormatter.formatSignedRupees(-summary.totalIOweOthers)
        binding.tvNetBalance.text = CurrencyFormatter.formatSignedRupees(summary.netBalance)

        // 2. Update People List & Empty State
        val currentQuery = binding.etSearchPeople.text?.toString().orEmpty()
        filterPeople(currentQuery)
    }

    private fun filterPeople(query: String) {
        val filtered = if (query.isBlank()) {
            allPeople
        } else {
            allPeople.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

        personAdapter.updateList(filtered)

        if (allPeople.isEmpty()) {
            binding.layoutEmptyPeople.visibility = View.VISIBLE
            binding.rvPeople.visibility = View.GONE
        } else {
            binding.layoutEmptyPeople.visibility = View.GONE
            binding.rvPeople.visibility = View.VISIBLE
        }
    }

    /**
     * Dialog for adding a new person.
     */
    private fun showAddPersonDialog() {
        val dialogBinding = DialogAddPersonBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Background transparency to respect custom card corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSavePerson.setOnClickListener {
            val name = dialogBinding.etPersonName.text?.toString().orEmpty()

            val (newId, error) = personRepository.addPerson(name)
            if (error != null) {
                dialogBinding.tilPersonName.error = error
            } else {
                dialogBinding.tilPersonName.error = null
                dialog.dismiss()
                Toast.makeText(this, "Added \"${name.trim()}\"", Toast.LENGTH_SHORT).show()
                loadDashboardData()

                // Immediately trigger background sync to save new contact to MongoDB Atlas
                triggerSilentSync()

                // Immediately open their account screen for convenience!
                val intent = Intent(this, PersonDetailActivity::class.java).apply {
                    putExtra(PersonDetailActivity.EXTRA_PERSON_ID, newId)
                }
                startActivity(intent)
            }
        }

        dialog.show()
    }

    /**
     * Background sync that updates the cloud without blocking the UI.
     */
    private fun triggerSilentSync() {
        lifecycleScope.launch(Dispatchers.IO) {
            SyncManager.sync(applicationContext)
            withContext(Dispatchers.Main) {
                loadDashboardData()
            }
        }
    }

    /**
     * Menu options dialog: About User, Backend Server URL, Log Out.
     */
    private fun showOptionsMenuDialog() {
        val displayName = sessionManager.getUserDisplayName()
        val currentUrl = ApiClient.getBaseUrl(this)
        val options = arrayOf(
            "About User ($displayName)",
            "Backend Server URL ($currentUrl)",
            "Log Out ($displayName)"
        )

        AlertDialog.Builder(this)
            .setTitle("Hisab Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAboutUserDialog()
                    1 -> showServerSettingsDialog()
                    2 -> confirmLogout()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAboutUserDialog() {
        val displayName = sessionManager.getUserDisplayName()
        val email = sessionManager.getUserEmail() ?: "Not available"
        val currentUrl = ApiClient.getBaseUrl(this)

        AlertDialog.Builder(this)
            .setTitle("About User")
            .setMessage(
                "Name: $displayName\n" +
                "Email: $email\n\n" +
                "Cloud Sync: Automatic (MongoDB Atlas)\n" +
                "Backend Server: $currentUrl\n" +
                "Account Status: Active & Secured"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showServerSettingsDialog() {
        val currentUrl = ApiClient.getBaseUrl(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val input = android.widget.EditText(this).apply {
            setText(currentUrl)
            hint = "http://192.168.10.129:5000/"
            setSingleLine()
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        container.addView(input)

        val hintText = android.widget.TextView(this).apply {
            text = "Presets:\n• Current Server: $currentUrl\n• Android Emulator: http://10.0.2.2:5000/\n• USB Cable (adb reverse): http://127.0.0.1:5000/\n\nNote: Always specify port :5000"
            textSize = 12f
            setPadding(0, 16, 0, 0)
            setTextColor(getColor(R.color.secondary_text))
        }
        container.addView(hintText)

        AlertDialog.Builder(this)
            .setTitle("Backend Server URL")
            .setMessage("Configure backend server URL:")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    ApiClient.setBaseUrl(this, newUrl)
                    Toast.makeText(this, "API URL saved: ${ApiClient.getBaseUrl(this)}", Toast.LENGTH_SHORT).show()
                    triggerSilentSync()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out of Hisab? Your data remains securely preserved.")
            .setPositiveButton("Log Out") { _, _ ->
                sessionManager.clearSession()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                redirectToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}