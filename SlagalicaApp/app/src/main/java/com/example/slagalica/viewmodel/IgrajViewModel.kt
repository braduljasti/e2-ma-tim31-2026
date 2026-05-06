package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class IgrajViewModel : ViewModel() {

    private val _tokens = MutableLiveData<Int>(5)
    val tokens: LiveData<Int> = _tokens

    private val _stars = MutableLiveData<Int>(0)
    val stars: LiveData<Int> = _stars

    private val _league = MutableLiveData<String>("Liga 0")
    val league: LiveData<String> = _league

    private val _searchingOpponent = MutableLiveData<Boolean>(false)
    val searchingOpponent: LiveData<Boolean> = _searchingOpponent

    fun searchOpponent() { _searchingOpponent.value = true }
    fun cancelSearch() { _searchingOpponent.value = false }
}
