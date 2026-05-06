package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val _loginSuccess = MutableLiveData<Boolean>()
    val loginSuccess: LiveData<Boolean> = _loginSuccess

    private val _loginError = MutableLiveData<String?>()
    val loginError: LiveData<String?> = _loginError

    private val _registrationSuccess = MutableLiveData<Boolean>()
    val registrationSuccess: LiveData<Boolean> = _registrationSuccess

    private val _registrationError = MutableLiveData<String?>()
    val registrationError: LiveData<String?> = _registrationError

    fun login(email: String, password: String) {
        viewModelScope.launch {
            delay(1500)
            if (email.isNotEmpty() && password.length >= 6) {
                _loginSuccess.value = true
                _loginError.value = null
            } else {
                _loginError.value = "Neispravni podaci za prijavu"
                _loginSuccess.value = false
            }
        }
    }

    fun register(email: String, username: String, region: String, password: String) {
        viewModelScope.launch {
            delay(1500)
            if (email.isNotEmpty() && username.length >= 3 && password.length >= 6) {
                _registrationSuccess.value = true
                _registrationError.value = null
            } else {
                _registrationError.value = "Registracija nije uspješna. Pokušajte ponovo."
                _registrationSuccess.value = false
            }
        }
    }
}
