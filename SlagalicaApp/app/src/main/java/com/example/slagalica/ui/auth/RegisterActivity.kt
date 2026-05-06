package com.example.slagalica.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.databinding.ActivityRegisterBinding
import com.example.slagalica.viewmodel.AuthViewModel
import com.google.android.material.snackbar.Snackbar

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        setupRegionDropdown()
        setupListeners()
        observeChanges()
    }

    private fun setupRegionDropdown() {
        val regions = resources.getStringArray(R.array.regioni)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, regions)
        binding.actvRegion.setAdapter(adapter)
        binding.actvRegion.setText(regions[0], false)
    }

    private fun setupListeners() {
        binding.btnRegistracija.setOnClickListener {
            val email = binding.etEmailReg.text.toString().trim()
            val username = binding.etKorisnickoIme.text.toString().trim()
            val region = binding.actvRegion.text.toString()
            val password = binding.etLozinkaReg.text.toString()
            val confirm = binding.etPotvrdiLozinku.text.toString()

            if (validateInput(email, username, password, confirm)) {
                viewModel.register(email, username, region, password)
            }
        }

        binding.btnNazadNaLogin.setOnClickListener { finish() }
    }

    private fun observeChanges() {
        viewModel.registrationSuccess.observe(this) { success ->
            if (success) {
                Snackbar.make(binding.root, getString(R.string.msg_verifikacioni_mejl), Snackbar.LENGTH_LONG).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
        }

        viewModel.registrationError.observe(this) { message ->
            if (message != null) {
                binding.tvGreskaReg.text = message
                binding.tvGreskaReg.visibility = View.VISIBLE
            }
        }
    }

    private fun validateInput(email: String, username: String, password: String, confirm: String): Boolean {
        var valid = true

        if (email.isEmpty()) {
            binding.tilEmailReg.error = getString(R.string.err_prazno_polje); valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailReg.error = getString(R.string.err_neispravan_email); valid = false
        } else binding.tilEmailReg.error = null

        if (username.isEmpty()) {
            binding.tilKorisnickoIme.error = getString(R.string.err_prazno_polje); valid = false
        } else if (username.length < 3) {
            binding.tilKorisnickoIme.error = "Minimum 3 characters"; valid = false
        } else binding.tilKorisnickoIme.error = null

        if (password.isEmpty()) {
            binding.tilLozinkaReg.error = getString(R.string.err_prazno_polje); valid = false
        } else if (password.length < 6) {
            binding.tilLozinkaReg.error = "Minimum 6 characters"; valid = false
        } else binding.tilLozinkaReg.error = null

        if (confirm.isEmpty()) {
            binding.tilPotvrdiLozinku.error = getString(R.string.err_prazno_polje); valid = false
        } else if (password != confirm) {
            binding.tilPotvrdiLozinku.error = getString(R.string.err_lozinke_ne_poklapaju); valid = false
        } else binding.tilPotvrdiLozinku.error = null

        return valid
    }

    override fun onResume() {
        super.onResume()
        binding.tvGreskaReg.visibility = View.GONE
    }
}
