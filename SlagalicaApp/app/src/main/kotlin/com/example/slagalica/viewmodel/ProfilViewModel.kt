package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.R
import com.example.slagalica.data.AuthRepository
import com.example.slagalica.data.ProfilRepository
import com.example.slagalica.model.GameResult
import com.example.slagalica.model.GameStatistic
import com.example.slagalica.model.GameType
import com.example.slagalica.model.Liga
import com.example.slagalica.model.PlayerStats
import com.example.slagalica.model.UserProfile
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ViewModel za ProfilFragment (spec 2).
 *
 * Profil se čita iz users/{uid} (korisničko ime, mejl, avatar, tokeni,
 * zvezde, liga, region), a statistika se računa iz users/{uid}/gameResults -
 * svaki odigrani meč nosi i detalje specifične za igru (vidi GameResult.details).
 */
class ProfilViewModel(
    private val profilRepo: ProfilRepository = ProfilRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    // === Profil ===
    private val _userProfile = MutableLiveData<UserProfile>()
    val userProfile: LiveData<UserProfile> = _userProfile

    // === Statistika ===
    private val _playerStats = MutableLiveData<PlayerStats>()
    val playerStats: LiveData<PlayerStats> = _playerStats

    /** Avatari koje korisnik može da izabere; indeks + 1 = avatarId u bazi. */
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
        viewModelScope.launch {
            val user = runCatching { profilRepo.ucitajKorisnika() }.getOrNull() ?: return@launch
            _userProfile.value = UserProfile(
                username = user.username,
                email = user.email,
                avatarResId = avatarRes(user.avatarId),
                tokens = user.tokens,
                totalStars = user.stars,
                league = Liga.fromIndex(user.league),
                region = user.region,
                // QR sadrži uid - prijatelj ga skenira da pošalje poziv (spec 2.a.viii)
                qrPayload = "slagalica://invite/${user.uid}"
            )
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val rezultati = runCatching { profilRepo.sviRezultati() }.getOrDefault(emptyList())
            _playerStats.value = izracunajStatistiku(rezultati)
        }
    }

    // ============================================================
    // STATISTIKA IZ ODIGRANIH MEČEVA (spec 2.c)
    // ============================================================

    private fun izracunajStatistiku(svi: List<GameResult>): PlayerStats {
        // 2.c.ii - odnos pogođenih i promašenih pitanja
        val kzz = statistikaIgre(svi, GameType.KO_ZNA_ZNA, "Pogođenih pitanja") { r ->
            val tacnih = sumDetail(r, "tacnih")
            val ukupno = tacnih + sumDetail(r, "netacnih") + sumDetail(r, "bezOdgovora")
            procenat(tacnih, ukupno)
        }
        // 2.c.vii - procenat uspešno povezanih pojmova
        val spojnice = statistikaIgre(svi, GameType.SPOJNICE, "Povezanih pojmova") { r ->
            procenat(sumDetail(r, "povezanih"), sumDetail(r, "pokusaja"))
        }
        // 2.c.v - odnos rešenih i nerešenih asocijacija
        val asocijacije = statistikaIgre(svi, GameType.ASOCIJACIJE, "Rešenih asocijacija") { r ->
            procenat(sumDetail(r, "resenihFinala"), sumDetail(r, "rundi"))
        }
        // 2.c.vi - pogođena kombinacija (po rundama)
        val skocko = statistikaIgre(svi, GameType.SKOCKO, "Pogođena kombinacija") { r ->
            procenat(sumDetail(r, "resenihRundi"), sumDetail(r, "rundi"))
        }
        // 2.c.iii - pronađen tačan broj (10 bodova = pogođen broj)
        val mojBroj = statistikaIgre(svi, GameType.MOJ_BROJ, "Pronađen tačan broj") { r ->
            procenat(r.count { it.myPoints >= 10 }.toLong(), r.size.toLong())
        }
        // 2.c.iv - pogođen pojam
        val korak = statistikaIgre(svi, GameType.KORAK_PO_KORAK, "Pogođen pojam") { r ->
            procenat(r.count { it.myPoints > 0 }.toLong(), r.size.toLong())
        }

        return PlayerStats(
            koZnaZna = kzz,
            mojBroj = mojBroj,
            korakPoKorak = korak,
            asocijacije = asocijacije,
            skocko = skocko,
            spojnice = spojnice,
            totalGamesPlayed = svi.size,                  // 2.c.viii
            totalWins = svi.count { it.won },             // 2.c.ix
            totalLosses = svi.count { !it.won }
        )
    }

    /** Gradi GameStatistic za jednu igru: opseg/prosek bodova + glavna metrika. */
    private fun statistikaIgre(
        svi: List<GameResult>,
        tip: GameType,
        metrikaLabel: String,
        metrika: (List<GameResult>) -> Float
    ): GameStatistic {
        val rezultati = svi.filter { it.gameType == tip.name }
        // 2.c.i - opseg prosečno osvojenih bodova
        val bodoviLabel = if (rezultati.isEmpty()) {
            "Još nema odigranih partija"
        } else {
            val bodovi = rezultati.map { it.myPoints }
            "Prosek: ${bodovi.average().roundToInt()} bodova " +
                    "(${bodovi.minOrNull()} – ${bodovi.maxOrNull()})"
        }
        return GameStatistic(
            gameName = tip.displayName,
            averagePointsLabel = bodoviLabel,
            mainMetricLabel = metrikaLabel,
            mainMetricPercent = if (rezultati.isEmpty()) 0f else metrika(rezultati),
            gamesPlayed = rezultati.size
        )
    }

    private fun sumDetail(rezultati: List<GameResult>, key: String): Long =
        rezultati.sumOf { it.details[key] ?: 0L }

    private fun procenat(deo: Long, celina: Long): Float =
        if (celina > 0) deo * 100f / celina else 0f

    // ============================================================
    // AKCIJE
    // ============================================================

    /** Spec 2.b - izmena avatara: odmah u UI, trajno u Firestore. */
    fun changeAvatar(avatarResId: Int) {
        val current = _userProfile.value ?: return
        _userProfile.value = current.copy(avatarResId = avatarResId)

        val avatarId = availableAvatars.indexOf(avatarResId) + 1
        if (avatarId > 0) {
            viewModelScope.launch { runCatching { profilRepo.sacuvajAvatar(avatarId) } }
        }
    }

    /** Spec 2.d - odjava sa Firebase naloga. */
    fun logout() = authRepo.logout()

    private fun avatarRes(avatarId: Int): Int =
        availableAvatars.getOrElse(avatarId - 1) { availableAvatars[0] }
}
