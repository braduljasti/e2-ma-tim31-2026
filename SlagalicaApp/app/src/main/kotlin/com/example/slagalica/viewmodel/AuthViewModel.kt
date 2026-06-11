package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.AuthRepository
import kotlinx.coroutines.launch

/**
 * ViewModel za registraciju, logovanje i promjenu lozinke.
 * Koristi AuthRepository (Firebase Auth + Firestore) umjesto ranije simulacije.
 */
class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _loginSuccess = MutableLiveData<Boolean>()
    val loginSuccess: LiveData<Boolean> = _loginSuccess

    private val _loginError = MutableLiveData<String?>()
    val loginError: LiveData<String?> = _loginError

    private val _registrationSuccess = MutableLiveData<Boolean>()
    val registrationSuccess: LiveData<Boolean> = _registrationSuccess

    private val _registrationError = MutableLiveData<String?>()
    val registrationError: LiveData<String?> = _registrationError

    private val _passwordChanged = MutableLiveData<Boolean>()
    val passwordChanged: LiveData<Boolean> = _passwordChanged

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    /** 1.d - login po mejlu ili korisnickom imenu. */
    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            try {
                repo.login(identifier, password)
                _loginSuccess.value = true
                _loginError.value = null
            } catch (e: Exception) {
                _loginError.value = e.message ?: "Neispravni podaci za prijavu"
                _loginSuccess.value = false
            }
        }
    }

    /** 1.a + 1.b - registracija uz slanje verifikacionog mejla. */
    fun register(email: String, username: String, region: String, password: String) {
        viewModelScope.launch {
            try {
                repo.register(email, username, region, password)
                _registrationSuccess.value = true
                _registrationError.value = null
            } catch (e: Exception) {
                _registrationError.value = e.message ?: "Registracija nije uspješna. Pokušajte ponovo."
                _registrationSuccess.value = false
            }
        }
    }

    /** 1.e - promjena lozinke (stara + nova dva puta provjerava se u formi). */
    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                repo.changePassword(oldPassword, newPassword)
                _passwordChanged.value = true
                _passwordError.value = null
            } catch (e: Exception) {
                _passwordError.value = e.message ?: "Promjena lozinke nije uspjela."
                _passwordChanged.value = false
            }
        }
    }

    fun isLoggedIn(): Boolean = repo.isLoggedIn() && repo.isEmailVerified()

    fun logout() = repo.logout()
}
