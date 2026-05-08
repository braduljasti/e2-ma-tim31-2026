package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.slagalica.R
import com.example.slagalica.model.GameStatistic
import com.example.slagalica.model.Liga
import com.example.slagalica.model.PlayerStats
import com.example.slagalica.model.UserProfile

/**
 * ViewModel za ProfilFragment.
 *
 * Drži dva LiveData stream-a:
 *  - userProfile: osnovni podaci o korisniku (zaglavlje profila)
 *  - playerStats: statistika po igrama + ukupno
 *
 * Pošto trenutno ne koristimo backend, podaci se hardkoduju u init bloku.
 * Kad se kasnije bude radio repository sloj, samo loadProfile/loadStats
 * funkcije će se izmeniti — Fragment ostaje isti.
 */
class ProfilViewModel : ViewModel() {

    // === Profil ===
    private val _userProfile = MutableLiveData<UserProfile>()
    val userProfile: LiveData<UserProfile> = _userProfile

    // === Statistika ===
    private val _playerStats = MutableLiveData<PlayerStats>()
    val playerStats: LiveData<PlayerStats> = _playerStats

    /**
     * Lista avatara koje korisnik može da izabere u dijalogu.
     * Drawable resursi iz Koraka 2.
     */
    val availableAvatars = listOf(
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4
    )

    init {
        loadProfile()
        loadStats()
    }

    private fun loadProfile() {
        _userProfile.value = UserProfile(
            username = "igrac_123",
            email = "igrac123@slagalica.rs",
            avatarResId = R.drawable.avatar_1,
            tokens = 1250,
            totalStars = 87,
            league = Liga.ZLATNA,
            region = "Vojvodina",
            qrPayload = "slagalica://invite/igrac_123"
        )
    }

    private fun loadStats() {
        _playerStats.value = PlayerStats(
            koZnaZna = GameStatistic(
                gameName = "Ko zna zna",
                averagePointsLabel = "Prosek: 40 - 60 bodova",
                mainMetricLabel = "Pogođenih pitanja",
                mainMetricPercent = 72f,
                gamesPlayed = 24
            ),
            mojBroj = GameStatistic(
                gameName = "Moj broj",
                averagePointsLabel = "Prosek: 50 - 80 bodova",
                mainMetricLabel = "Pronađen tačan broj",
                mainMetricPercent = 68f,
                gamesPlayed = 31
            ),
            korakPoKorak = GameStatistic(
                gameName = "Korak po korak",
                averagePointsLabel = "Prosek: 30 - 70 bodova",
                mainMetricLabel = "Pogođen pojam",
                mainMetricPercent = 55f,
                gamesPlayed = 18
            ),
            asocijacije = GameStatistic(
                gameName = "Asocijacije",
                averagePointsLabel = "Prosek: 60 - 90 bodova",
                mainMetricLabel = "Rešeno asocijacija",
                mainMetricPercent = 80f,
                gamesPlayed = 22
            ),
            skocko = GameStatistic(
                gameName = "Skočko",
                averagePointsLabel = "Prosek: 45 - 75 bodova",
                mainMetricLabel = "Pogođena kombinacija",
                mainMetricPercent = 64f,
                gamesPlayed = 28
            ),
            spojnice = GameStatistic(
                gameName = "Spojnice",
                averagePointsLabel = "Prosek: 35 - 65 bodova",
                mainMetricLabel = "Povezanih pojmova",
                mainMetricPercent = 78f,
                gamesPlayed = 19
            ),
            totalGamesPlayed = 142,
            totalWins = 92,
            totalLosses = 50
        )
    }

    /**
     * Poziva se kad korisnik izabere novi avatar iz dijaloga.
     * Pravi novu instancu UserProfile-a sa promenjenim avatarom
     * i okidaju se observer-i u Fragmentu.
     */
    fun changeAvatar(avatarResId: Int) {
        val current = _userProfile.value ?: return
        _userProfile.value = current.copy(avatarResId = avatarResId)
    }
}