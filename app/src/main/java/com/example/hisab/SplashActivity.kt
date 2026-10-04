package com.example.hisab

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.hisab.api.SessionManager
import com.example.hisab.auth.LoginActivity
import com.example.hisab.databinding.ActivitySplashBinding

/**
 * Brand Launch Screen for Hisab.
 * 
 * Displays the Hisab logo and branding animation, validates session authentication,
 * and routes directly to Dashboard or Login seamlessly.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager.getInstance(this)

        // Smooth entrance animation for Hisab logo & branding
        binding.ivLogo.alpha = 0f
        binding.ivLogo.scaleX = 0.8f
        binding.ivLogo.scaleY = 0.8f
        binding.ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.tvAppName.alpha = 0f
        binding.tvAppName.animate()
            .alpha(1f)
            .setStartDelay(200)
            .setDuration(600)
            .start()

        binding.tvTagline.alpha = 0f
        binding.tvTagline.animate()
            .alpha(1f)
            .setStartDelay(350)
            .setDuration(600)
            .start()

        // After branding showcase, route based on session state
        Handler(Looper.getMainLooper()).postDelayed({
            proceedToNextScreen()
        }, 1200)
    }

    private fun proceedToNextScreen() {
        val nextActivity = if (sessionManager.isLoggedIn()) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, nextActivity))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
