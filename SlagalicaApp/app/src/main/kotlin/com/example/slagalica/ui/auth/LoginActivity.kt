package com.example.slagalica.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.databinding.ActivityLoginBinding
import com.example.slagalica.ui.main.MainActivity
import com.example.slagalica.viewmodel.AuthViewModel
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        setupListeners()
        observeChanges()
    }

    private fun setupListeners() {
        binding.btnPrijava.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etLozinka.text.toString()
            if (validateInput(email, password)) {
                showLoading(true)
                viewModel.login(email, password)
            }
        }
        binding.btnIdiNaRegistraciju.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun observeChanges() {
        viewModel.loginSuccess.observe(this) { success ->
            showLoading(false)
            if (success) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        viewModel.loginError.observe(this) { message ->
            showLoading(false)
            if (message != null) showError(message)
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var valid = true
        // 1.d - dozvoljen je mejl ILI korisnicko ime, pa ne forsiramo email format
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.err_prazno_polje); valid = false
        } else {
            binding.tilEmail.error = null
        }
        if (password.isEmpty()) {
            binding.tilLozinka.error = getString(R.string.err_prazno_polje); valid = false
        } else {
            binding.tilLozinka.error = null
        }
        return valid
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBarLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnPrijava.isEnabled = !loading
        binding.btnIdiNaRegistraciju.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvGreska.text = message
        binding.tvGreska.visibility = View.VISIBLE
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        binding.tvGreska.visibility = View.GONE
    }
}
