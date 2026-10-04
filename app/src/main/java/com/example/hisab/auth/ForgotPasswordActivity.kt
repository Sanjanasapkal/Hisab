package com.example.hisab.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hisab.api.ApiClient
import com.example.hisab.api.ForgotPasswordRequest
import com.example.hisab.databinding.ActivityForgotPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Authentication Screen: Forgot Password
 * 
 * Submits registered email to receive a password reset token/link.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.layoutHeader)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSubmit.setOnClickListener {
            handleSubmit()
        }

        binding.tvHaveToken.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }

    private fun handleSubmit() {
        val email = binding.etEmail.text?.toString().orEmpty().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Please enter a valid email address"
            return
        } else {
            binding.tilEmail.error = null
        }

        setLoading(true)
        binding.tvStatusMessage.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.getService(this@ForgotPasswordActivity)
                val response = apiService.forgotPassword(ForgotPasswordRequest(email = email))

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    android.widget.Toast.makeText(
                        this@ForgotPasswordActivity,
                        "A 6-digit reset code has been sent to $email!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    val intent = Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    binding.tvStatusMessage.text = "Connection error: ${e.localizedMessage}"
                    binding.tvStatusMessage.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSubmit.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
