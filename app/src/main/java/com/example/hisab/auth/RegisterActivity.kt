package com.example.hisab.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hisab.api.ApiClient
import com.example.hisab.api.RegisterRequest
import com.example.hisab.databinding.ActivityRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Authentication Screen: Register
 * 
 * Validates registration details and initiates email OTP verification.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.layoutHeader)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.tvLogin.setOnClickListener { finish() }

        binding.btnRegister.setOnClickListener {
            handleRegister()
        }
    }

    private fun handleRegister() {
        val name = binding.etUsername.text?.toString().orEmpty().trim()
        val email = binding.etEmail.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        var hasError = false

        // 1. Name Validation
        if (name.isBlank()) {
            binding.tilUsername.error = "Please enter your name"
            hasError = true
        } else {
            binding.tilUsername.error = null
        }

        // 2. Email Validation
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Enter a valid email address"
            hasError = true
        } else {
            binding.tilEmail.error = null
        }

        // 3. Password Validation
        if (password.length < 8) {
            binding.tilPassword.error = "Password must be at least 8 characters"
            hasError = true
        } else if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            binding.tilPassword.error = "Password must contain letters and numbers"
            hasError = true
        } else {
            binding.tilPassword.error = null
        }

        // 4. Confirm Password Validation
        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            hasError = true
        } else {
            binding.tilConfirmPassword.error = null
        }

        if (hasError) return

        setLoading(true)
        binding.tvErrorMessage.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.getService(this@RegisterActivity)
                val response = apiService.register(
                    RegisterRequest(
                        name = name,
                        username = name,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword
                    )
                )

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration initiated! Check your email for the code.",
                            Toast.LENGTH_LONG
                        ).show()

                        val intent = Intent(this@RegisterActivity, VerifyOtpActivity::class.java).apply {
                            putExtra("extra_email", email)
                            putExtra("extra_username", name)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val errorJson = response.errorBody()?.string()
                        val message = try {
                            JSONObject(errorJson.orEmpty()).optString("message", "Registration failed")
                        } catch (e: Exception) {
                            "Registration failed (${response.code()})"
                        }

                        binding.tvErrorMessage.text = message
                        binding.tvErrorMessage.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    binding.tvErrorMessage.text = "Connection error: ${e.localizedMessage}"
                    binding.tvErrorMessage.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
