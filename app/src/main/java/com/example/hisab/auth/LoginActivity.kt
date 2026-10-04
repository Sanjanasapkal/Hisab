package com.example.hisab.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hisab.MainActivity
import com.example.hisab.R
import com.example.hisab.api.ApiClient
import com.example.hisab.api.LoginRequest
import com.example.hisab.api.SessionManager
import com.example.hisab.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Authentication Screen: Login
 * 
 * Supports authentication with either username or email.
 * Securely stores JWT token upon success using [SessionManager].
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager.getInstance(this)

        // If already logged in, navigate straight to dashboard
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.layoutHeader)

        // Pre-fill email if passed from Registration/OTP
        intent.getStringExtra("extra_email")?.let {
            binding.etUsername.setText(it)
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.btnServerSettings.setOnClickListener {
            showServerSettingsDialog()
        }
    }

    private fun handleLogin() {
        val email = binding.etUsername.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty()

        var hasError = false

        if (email.isEmpty()) {
            binding.tilUsername.error = "Email address is required"
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilUsername.error = "Please enter a valid email address"
            hasError = true
        } else {
            binding.tilUsername.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            hasError = true
        } else {
            binding.tilPassword.error = null
        }

        if (hasError) return

        setLoading(true)
        binding.tvErrorMessage.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.getService(this@LoginActivity)
                val response = apiService.login(LoginRequest(email = email, password = password))

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.isSuccessful && response.body()?.data != null) {
                        val body = response.body()!!
                        val token = body.data!!.token
                        val user = body.data!!.user

                        // Save secure credentials in Keystore EncryptedSharedPreferences
                        sessionManager.saveSession(token, user)

                        val displayName = user.name ?: user.username ?: "User"
                        Toast.makeText(this@LoginActivity, "Welcome back, $displayName!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else if (response.code() == 403) {
                        // Unverified account
                        val rawError = response.errorBody()?.string()
                        val msg = "Please verify your email address before logging in."
                        binding.tvErrorMessage.text = msg
                        binding.tvErrorMessage.visibility = View.VISIBLE

                        // Prompt user to verify OTP
                        AlertDialog.Builder(this@LoginActivity)
                            .setTitle("Email Verification Required")
                            .setMessage("Your email address has not been verified yet. Would you like to enter your 6-digit verification code now?")
                            .setPositiveButton("Verify Code") { _, _ ->
                                val intent = Intent(this@LoginActivity, VerifyOtpActivity::class.java).apply {
                                    putExtra("extra_email", identifier)
                                }
                                startActivity(intent)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        // 401 or invalid credentials
                        val serverMessage = try {
                            val raw = response.errorBody()?.string()
                            if (!raw.isNullOrBlank()) {
                                JSONObject(raw).optString("message")
                            } else null
                        } catch (_: Exception) { null }

                        val errorMsg = if (!serverMessage.isNullOrBlank()) {
                            serverMessage
                        } else if (response.code() == 401) {
                            "Invalid email or password"
                        } else {
                            "Login failed (${response.code()}). Please try again."
                        }
                        binding.tvErrorMessage.text = errorMsg
                        binding.tvErrorMessage.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    val activeUrl = ApiClient.getBaseUrl(this@LoginActivity)
                    val errorMsg = "Cannot connect to server at $activeUrl\n(${e.localizedMessage})\n\n👉 Tap here or the gear icon above to update your backend URL."
                    binding.tvErrorMessage.text = errorMsg
                    binding.tvErrorMessage.visibility = View.VISIBLE
                    binding.tvErrorMessage.setOnClickListener {
                        showServerSettingsDialog()
                    }
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showServerSettingsDialog() {
        val currentUrl = ApiClient.getBaseUrl(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val input = EditText(this).apply {
            setText(currentUrl)
            hint = "http://10.86.189.171:5000/"
            setSingleLine()
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        container.addView(input)

        val hintText = TextView(this).apply {
            text = "Presets (tap button below to use):\n• Physical Phone (Wi-Fi): http://10.86.189.171:5000/\n• Android Emulator: http://10.0.2.2:5000/\n• USB Cable (adb reverse): http://127.0.0.1:5000/\n\nNote: Always specify port :5000"
            textSize = 12f
            setPadding(0, 16, 0, 0)
            setTextColor(getColor(R.color.secondary_text))
        }
        container.addView(hintText)

        AlertDialog.Builder(this)
            .setTitle("Backend API URL")
            .setMessage("Set the backend URL for your network:")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    ApiClient.setBaseUrl(this, newUrl)
                    binding.tvErrorMessage.visibility = View.GONE
                    Toast.makeText(this, "API URL saved: ${ApiClient.getBaseUrl(this)}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Use 10.86.189.171:5000") { _, _ ->
                ApiClient.setBaseUrl(this, "http://10.86.189.171:5000/")
                binding.tvErrorMessage.visibility = View.GONE
                Toast.makeText(this, "API URL saved: http://10.86.189.171:5000/", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
