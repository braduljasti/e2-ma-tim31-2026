package com.example.slagalica.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.slagalica.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            // Ako postoji aktivna sesija i mejl je potvrđen, idemo direktno u aplikaciju
            val user = com.example.slagalica.data.FirebaseProvider.auth.currentUser
            val target = if (user != null && user.isEmailVerified) {
                Intent(this, com.example.slagalica.ui.main.MainActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(target)
            finish()
        }, SPLASH_DELAY)
    }

    companion object {
        private const val SPLASH_DELAY = 5000L
    }
}
