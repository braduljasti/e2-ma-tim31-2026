package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.RegionRepository
import com.example.slagalica.model.IgracTacka
import com.example.slagalica.model.RegionRangRed
import kotlinx.coroutines.launch

/**
 * ViewModel ekrana regiona (spec 5.a, 5.b): tačke igrača za mapu i
 * mjesečna rang lista po regionima.
 */
class RegioniViewModel(
    private val repo: RegionRepository = RegionRepository()
) : ViewModel() {

    private val _tacke = MutableLiveData<List<IgracTacka>>(emptyList())
    val tacke: LiveData<List<IgracTacka>> = _tacke

    private val _rang = MutableLiveData<List<RegionRangRed>>(emptyList())
    val rang: LiveData<List<RegionRangRed>> = _rang

    init { osvjezi() }

    fun osvjezi() {
        viewModelScope.launch {
            _tacke.value = runCatching { repo.tackeIgraca() }.getOrDefault(emptyList())
            _rang.value = runCatching { repo.rangPoRegionima() }.getOrDefault(emptyList())
        }
    }
}
