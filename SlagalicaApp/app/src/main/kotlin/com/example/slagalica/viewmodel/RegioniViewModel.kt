package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.RegionRepository
import com.example.slagalica.model.IgracTacka
import com.example.slagalica.model.RegionRangRed
import com.example.slagalica.model.RegionStatistika
import kotlinx.coroutines.launch

class RegioniViewModel(
    private val repo: RegionRepository = RegionRepository()
) : ViewModel() {

    private val _tacke = MutableLiveData<List<IgracTacka>>(emptyList())
    val tacke: LiveData<List<IgracTacka>> = _tacke

    private val _rang = MutableLiveData<List<RegionRangRed>>(emptyList())
    val rang: LiveData<List<RegionRangRed>> = _rang

    private val _statistika = MutableLiveData<RegionStatistika?>()
    val statistika: LiveData<RegionStatistika?> = _statistika

    init { osvjezi() }

    fun ucitajStatistiku(naziv: String) {
        viewModelScope.launch {
            _statistika.value = runCatching { repo.statistikaRegiona(naziv) }.getOrNull()
        }
    }

    fun consumeStatistika() { _statistika.value = null }

    fun osvjezi() {
        viewModelScope.launch {
            _tacke.value = runCatching { repo.tackeIgraca() }.getOrDefault(emptyList())
            _rang.value = runCatching { repo.rangPoRegionima() }.getOrDefault(emptyList())
        }
    }
}
