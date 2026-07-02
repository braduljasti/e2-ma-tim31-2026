package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.slagalica.data.ProfilRepository
import com.example.slagalica.model.Liga
import com.google.firebase.firestore.ListenerRegistration

class IgrajViewModel(
    private val profilRepo: ProfilRepository = ProfilRepository()
) : ViewModel() {

    private val _tokens = MutableLiveData(5)
    val tokens: LiveData<Int> = _tokens

    private val _stars = MutableLiveData(0)
    val stars: LiveData<Int> = _stars

    private val _league = MutableLiveData("Nulta liga")
    val league: LiveData<String> = _league

    private var listener: ListenerRegistration? = null

    init {
        // Dnevna dodjela tokena (5 + bonus lige) radi se u MainActivity preko
        // ProgressionRepository.reconcileOnStart() - ovdje samo pratimo profil uživo.
        listener = profilRepo.slusajKorisnika { user ->
            if (user == null) return@slusajKorisnika
            _tokens.postValue(user.tokens)
            _stars.postValue(user.stars)
            _league.postValue(Liga.fromIndex(user.league).displayName)
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
