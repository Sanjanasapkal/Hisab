package com.example.hisab.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hisab.R
import com.example.hisab.api.ApiClient
import com.example.hisab.api.ResetPasswordRequest
import com.example.hisab.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Authentication Screen: Reset Password
 * 
 * Takes reset token and allows the user to set a new password.
 * Supports deep linking (e.g. from an emailed reset link: hisab://reset-password?token=...)
 * or explicit manual input.
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.layoutHeader)

        binding.btnBack.setOnClickListener { finish() }

        // Check if token was passed via intent extras or deep link data
        val tokenFromExtra = intent.getStringExtra("extra_token")
        val tokenFromData = intent.data?.getQueryParameter("token")
        val initialToken = tokenFromExtra ?: tokenFromData

        if (!initialToken.isNullOrBlank()) {
            binding.etToken.setText(initialToken)
        }

        binding.btnResetPassword.setOnClickListener {
            handleResetPassword()
        }
    }

    private fun handleResetPassword() {
        val token = binding.etToken.text?.toString().orEmpty().trim()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        var hasError = false

        if (token.isBlank()) {
            binding.tilToken.error = "6-digit reset code is required"
            hasError = true
        } else {
            binding.tilToken.error = null
        }

        if (newPassword.length < 8 || !newPassword.any { it.isDigit() } || !newPassword.any { it.isLetter() }) {
            binding.tilNewPassword.error = "Password must be at least 8 characters with letters & numbers"
            hasError = true
        } else {
            binding.tilNewPassword.error = null
        }

        if (confirmPassword != newPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            hasError = true
        } else {
            binding.tilConfirmPassword.error = null
        }

        if (hasError) return

        setLoading(true)
        binding.tvStatusMessage.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.getService(this@ResetPasswordActivity)
                val response = apiService.resetPassword(
                    ResetPasswordRequest(
                        token = token,
                        newPassword = newPassword,
                        confirmPassword = confirmPassword
                    )
                )

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(
                            this@ResetPasswordActivity,
                            "Password has been reset successfully! Please log in.",
                            Toast.LENGTH_LONG
                        ).show()

                        val intent = Intent(this@ResetPasswordActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val errorJson = response.errorBody()?.string()
                        val message = try {
                            JSONObject(errorJson.orEmpty()).optString("message", "Password reset failed")
                        } catch (e: Exception) {
                            "Reset code is invalid or expired. Please check your email or request a new code."
                        }

                        binding.tvStatusMessage.text = message
                        binding.tvStatusMessage.setBackgroundResource(R.drawable.bg_badge_negative)
                        binding.tvStatusMessage.setTextColor(getColor(R.color.negative_balance))
                        binding.tvStatusMessage.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    binding.tvStatusMessage.text = "Connection error: ${e.localizedMessage}"
                    binding.tvStatusMessage.setBackgroundResource(R.drawable.bg_badge_negative)
                    binding.tvStatusMessage.setTextColor(getColor(R.color.negative_balance))
                    binding.tvStatusMessage.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnResetPassword.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
