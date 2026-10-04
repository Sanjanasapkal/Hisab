package com.example.hisab.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hisab.api.ApiClient
import com.example.hisab.api.ForgotUsernameRequest
import com.example.hisab.databinding.ActivityForgotUsernameBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Authentication Screen: Forgot Username
 * 
 * Submits registered email to receive username via email.
 */
class ForgotUsernameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotUsernameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotUsernameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.layoutHeader)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSubmit.setOnClickListener {
            handleSubmit()
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
                val apiService = ApiClient.getService(this@ForgotUsernameActivity)
                val response = apiService.forgotUsername(ForgotUsernameRequest(email))

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    // Always display generic message regardless of whether account was found
                    binding.tvStatusMessage.text = "If an account is registered with $email, an email containing your username has been sent. Please check your inbox."
                    binding.tvStatusMessage.visibility = View.VISIBLE
                    binding.btnSubmit.isEnabled = false
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
