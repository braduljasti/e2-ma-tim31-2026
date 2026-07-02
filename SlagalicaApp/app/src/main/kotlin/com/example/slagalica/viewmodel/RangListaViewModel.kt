package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.FirebaseProvider
import com.example.slagalica.data.RangListaRepository
import com.example.slagalica.model.RangCiklus
import com.example.slagalica.model.RangListaStavka
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RangListaViewModel(
    private val repo: RangListaRepository = RangListaRepository()
) : ViewModel() {

    companion object {
        private const val OSVJEZAVANJE_MS = 2 * 60 * 1000L // 2 minuta, po specifikaciji (4.d)
    }

    val uid: String get() = FirebaseProvider.currentUid ?: ""

    private val _ciklus = MutableLiveData(RangCiklus.NEDELJNI)
    val ciklus: LiveData<RangCiklus> = _ciklus

    private val _stavke = MutableLiveData<List<RangListaStavka>>(emptyList())
    val stavke: LiveData<List<RangListaStavka>> = _stavke

    private val _opsegDatuma = MutableLiveData("")
    val opsegDatuma: LiveData<String> = _opsegDatuma

    private val _ucitavanje = MutableLiveData(false)
    val ucitavanje: LiveData<Boolean> = _ucitavanje

    private var osvjezavajId = 0
    private var pokrenuto = false

    fun pokreni() {
        if (pokrenuto) return
        pokrenuto = true
        pokreniPeriodicnoOsvjezavanje()
        ucitaj()
    }

    fun promeniCiklus(novi: RangCiklus) {
        if (_ciklus.value == novi) return
        _ciklus.value = novi
        ucitaj()
    }

    private fun ucitaj() {
        val trenutni = _ciklus.value ?: RangCiklus.NEDELJNI
        _opsegDatuma.value = RangListaRepository.cycleDateRange(trenutni)
        _ucitavanje.value = true
        viewModelScope.launch {
            val lista = runCatching { repo.ucitajRangListu(trenutni) }.getOrDefault(emptyList())
            _stavke.value = lista
            _ucitavanje.value = false
        }
    }

    private fun pokreniPeriodicnoOsvjezavanje() {
        val mojId = ++osvjezavajId
        viewModelScope.launch {
            while (mojId == osvjezavajId) {
                delay(OSVJEZAVANJE_MS)
                if (mojId != osvjezavajId) break
                ucitaj()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        osvjezavajId++ // zaustavlja petlju za osvežavanje
    }
}
