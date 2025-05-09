package com.example.studybuddy

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Find the logo ImageView
        val logoImageView = findViewById<ImageView>(R.id.splash_logo)

        // Load and start the animation
        val splashAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_animation)
        logoImageView.startAnimation(splashAnimation)

        // Wait for 3 seconds before transitioning to MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            // Start the MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

            // Add a smooth transition between activities
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

            // Close the splash screen activity
            finish()
        }, 3000) // 3000 milliseconds = 3 seconds
    }
}