package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.LeagueManager
import com.example.slagalica.data.ProfilRepository
import com.example.slagalica.model.Liga
import com.example.slagalica.model.LigaPregled
import com.example.slagalica.model.LigaRed
import kotlinx.coroutines.launch

class LigeViewModel(
    private val profilRepo: ProfilRepository = ProfilRepository()
) : ViewModel() {

    private val _pregled = MutableLiveData<LigaPregled>()
    val pregled: LiveData<LigaPregled> = _pregled

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val user = runCatching { profilRepo.ucitajKorisnika() }.getOrNull() ?: return@launch
            _pregled.value = sastavi(user.stars, user.league)
        }
    }

    private fun sastavi(stars: Int, ligaIndex: Int): LigaPregled {
        val sveLige = Liga.values()
        val redovi = sveLige.mapIndexed { i, liga ->
            LigaRed(
                liga = liga,
                prag = LeagueManager.pragLige(i),
                tokeniDan = LeagueManager.tokeniPoDanu(i),
                jeTrenutna = i == ligaIndex
            )
        }

        val jeMaxLiga = ligaIndex >= sveLige.size - 1
        val sledeciPrag = if (jeMaxLiga) null else LeagueManager.pragLige(ligaIndex + 1)
        val pragTrenutne = LeagueManager.pragLige(ligaIndex)
        val progress = if (sledeciPrag == null || sledeciPrag <= pragTrenutne) {
            100
        } else {
            ((stars - pragTrenutne) * 100 / (sledeciPrag - pragTrenutne)).coerceIn(0, 100)
        }

        return LigaPregled(
            trenutnaLiga = Liga.fromIndex(ligaIndex),
            stars = stars,
            sledeciPrag = sledeciPrag,
            progressPercent = progress,
            redovi = redovi
        )
    }
}
