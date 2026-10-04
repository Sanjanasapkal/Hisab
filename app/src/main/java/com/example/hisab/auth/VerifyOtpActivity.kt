package com.example.hisab.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hisab.api.ApiClient
import com.example.hisab.api.ResendOtpRequest
import com.example.hisab.api.VerifyOtpRequest
import com.example.hisab.databinding.ActivityVerifyOtpBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Authentication Screen: Email OTP Verification
 * 
 * Submits 6-digit code to verify pending registration.
 * Includes a 60-second cooldown timer for requesting new codes.
 */
class VerifyOtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyOtpBinding
    private var email: String = ""
    private var username: String = ""
    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.hisab.util.StatusBarUtil.applyStatusBarPadding(binding.layoutHeader)

        email = intent.getStringExtra("extra_email").orEmpty()
        username = intent.getStringExtra("extra_username").orEmpty()

        if (email.isNotEmpty()) {
            binding.tvEmailSubtitle.text = "Enter the 6-digit code sent to\n$email"
        }

        setupListeners()
        startResendTimer(60)
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnVerify.setOnClickListener {
            handleVerify()
        }

        binding.btnResendOtp.setOnClickListener {
            handleResend()
        }
    }

    private fun handleVerify() {
        val otp = binding.etOtp.text?.toString().orEmpty().trim()

        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            binding.tilOtp.error = "Please enter all 6 digits"
            return
        } else {
            binding.tilOtp.error = null
        }

        setLoading(true)
        binding.tvStatusMessage.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.getService(this@VerifyOtpActivity)
                val response = apiService.verifyEmailOtp(VerifyOtpRequest(email, otp))

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(
                            this@VerifyOtpActivity,
                            "Email verified successfully! You can now log in.",
                            Toast.LENGTH_LONG
                        ).show()

                        val intent = Intent(this@VerifyOtpActivity, LoginActivity::class.java).apply {
                            putExtra("extra_email", email)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val errorJson = response.errorBody()?.string()
                        val message = try {
                            JSONObject(errorJson.orEmpty()).optString("message", "Verification failed")
                        } catch (e: Exception) {
                            "Verification failed. Please check your code."
                        }

                        binding.tvStatusMessage.text = message
                        binding.tvStatusMessage.visibility = View.VISIBLE
                    }
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

    private fun handleResend() {
        binding.btnResendOtp.isEnabled = false
        binding.tvStatusMessage.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.getService(this@VerifyOtpActivity)
                val response = apiService.resendEmailOtp(ResendOtpRequest(email))

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@VerifyOtpActivity, "New code sent to $email", Toast.LENGTH_SHORT).show()
                        startResendTimer(60)
                    } else {
                        val errorJson = response.errorBody()?.string()
                        val message = try {
                            JSONObject(errorJson.orEmpty()).optString("message", "Resend failed")
                        } catch (e: Exception) {
                            "Failed to resend code"
                        }
                        binding.tvStatusMessage.text = message
                        binding.tvStatusMessage.visibility = View.VISIBLE
                        binding.btnResendOtp.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatusMessage.text = "Connection error: ${e.localizedMessage}"
                    binding.tvStatusMessage.visibility = View.VISIBLE
                    binding.btnResendOtp.isEnabled = true
                }
            }
        }
    }

    private fun startResendTimer(seconds: Long) {
        binding.btnResendOtp.isEnabled = false
        resendTimer?.cancel()

        resendTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = millisUntilFinished / 1000
                binding.tvResendTimer.text = "Resend code in ${secLeft}s"
            }

            override fun onFinish() {
                binding.tvResendTimer.text = "Didn't receive the code?"
                binding.btnResendOtp.isEnabled = true
            }
        }.start()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnVerify.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
