package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.AuthRepository
import com.example.slagalica.data.ChatRepository
import com.example.slagalica.data.FirebaseProvider
import com.example.slagalica.model.ChatMessage
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repo: ChatRepository = ChatRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    val uid: String get() = FirebaseProvider.currentUid ?: ""

    private val _poruke = MutableLiveData<List<ChatMessage>>(emptyList())
    val poruke: LiveData<List<ChatMessage>> = _poruke

    private val _region = MutableLiveData<String>("")
    val region: LiveData<String> = _region

    private var listener: ListenerRegistration? = null
    private var mojeIme: String = "Igrač"

    fun pokreni() {
        viewModelScope.launch {
            val profil = runCatching { authRepo.currentUserProfile() }.getOrNull()
            mojeIme = profil?.username ?: "Igrač"
            val myRegion = profil?.region.orEmpty()
            _region.value = myRegion
            listener?.remove()
            listener = repo.listen(myRegion) { lista -> _poruke.postValue(lista) }
        }
    }

    fun posalji(tekst: String) {
        if (tekst.isBlank()) return
        val myRegion = _region.value.orEmpty()
        viewModelScope.launch {
            runCatching { repo.posalji(myRegion, uid, mojeIme, tekst) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
