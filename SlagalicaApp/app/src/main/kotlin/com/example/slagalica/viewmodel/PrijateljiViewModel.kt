package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.FriendsRepository
import com.example.slagalica.model.PrijateljItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel za ekran prijatelja (spec 7.a, 7.b).
 *
 * Ima dva moda u istom ekranu:
 *  - prazna pretraga -> prikazuje listu mojih prijatelja
 *  - unesen tekst    -> prikazuje rezultate pretrage (sa "Dodaj"/"Ukloni")
 */
class PrijateljiViewModel(
    private val repo: FriendsRepository = FriendsRepository()
) : ViewModel() {

    private val _stavke = MutableLiveData<List<PrijateljItem>>(emptyList())
    val stavke: LiveData<List<PrijateljItem>> = _stavke

    private val _prazno = MutableLiveData(false)
    val prazno: LiveData<Boolean> = _prazno

    private val _ucitavanje = MutableLiveData(false)
    val ucitavanje: LiveData<Boolean> = _ucitavanje

    private var trenutniUpit: String = ""
    private var searchJob: Job? = null

    init {
        prikaziPrijatelje()
    }

    /** Lista mojih prijatelja (mod kad je pretraga prazna). */
    fun prikaziPrijatelje() {
        trenutniUpit = ""
        viewModelScope.launch {
            _ucitavanje.value = true
            val lista = runCatching { repo.listaPrijatelja() }.getOrDefault(emptyList())
            _stavke.value = lista.map { PrijateljItem(it, jePrijatelj = true) }
            _prazno.value = lista.isEmpty()
            _ucitavanje.value = false
        }
    }

    /** Pretraga sa debounce-om (300 ms) da ne gađamo bazu na svaki pritisak tipke. */
    fun pretrazi(upit: String) {
        trenutniUpit = upit
        searchJob?.cancel()
        if (upit.isBlank()) { prikaziPrijatelje(); return }
        searchJob = viewModelScope.launch {
            delay(300)
            _ucitavanje.value = true
            val prijateljiUidovi = runCatching { repo.prijateljiUidovi() }.getOrDefault(emptySet())
            val rezultati = runCatching { repo.pretrazi(upit) }.getOrDefault(emptyList())
            _stavke.value = rezultati.map {
                PrijateljItem(it, jePrijatelj = it.uid in prijateljiUidovi)
            }
            _prazno.value = rezultati.isEmpty()
            _ucitavanje.value = false
        }
    }

    fun dodaj(friendUid: String) {
        viewModelScope.launch {
            runCatching { repo.dodajPrijatelja(friendUid) }
            osvjezi()
        }
    }

    fun ukloni(friendUid: String) {
        viewModelScope.launch {
            runCatching { repo.ukloniPrijatelja(friendUid) }
            osvjezi()
        }
    }

    /** Po akciji ponovo izvrši tekući mod (lista ili ista pretraga). */
    private fun osvjezi() {
        if (trenutniUpit.isBlank()) prikaziPrijatelje() else pretrazi(trenutniUpit)
    }
}
