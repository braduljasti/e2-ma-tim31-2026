package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.R
import com.example.slagalica.data.AuthRepository
import com.example.slagalica.data.Cycles
import com.example.slagalica.data.ProfilRepository
import com.example.slagalica.data.RegionStandingsRepository
import com.example.slagalica.model.GameResult
import com.example.slagalica.model.GameStatistic
import com.example.slagalica.model.GameType
import com.example.slagalica.model.Liga
import com.example.slagalica.model.PlayerStats
import com.example.slagalica.model.UserProfile
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProfilViewModel(
    private val profilRepo: ProfilRepository = ProfilRepository(),
    private val authRepo: AuthRepository = AuthRepository(),
    private val standingsRepo: RegionStandingsRepository = RegionStandingsRepository()
) : ViewModel() {

    private val _userProfile = MutableLiveData<UserProfile>()
    val userProfile: LiveData<UserProfile> = _userProfile

    private val _playerStats = MutableLiveData<PlayerStats>()
    val playerStats: LiveData<PlayerStats> = _playerStats

    // Boja okvira avatara po plasmanu regiona u prošlom ciklusu (spec 5.e); null = default.
    private val _okvirBoja = MutableLiveData<Int?>(null)
    val okvirBoja: LiveData<Int?> = _okvirBoja

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

                qrPayload = "slagalica://invite/${user.uid}"
            )
            odrediOkvir(user.region)
        }
    }

    /** Spec 5.e: ako je region igrača u prošlom ciklusu bio 1./2./3., okvir je zlatni/srebrni/bronzani. */
    private suspend fun odrediOkvir(region: String) {
        val poredak = runCatching { standingsRepo.poredakZa(Cycles.prethodniMjesec()) }.getOrNull()
        _okvirBoja.value = when (poredak?.indexOf(region)) {
            0 -> R.color.medalja_zlato
            1 -> R.color.medalja_srebro
            2 -> R.color.medalja_bronza
            else -> null
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val rezultati = runCatching { profilRepo.sviRezultati() }.getOrDefault(emptyList())
            _playerStats.value = izracunajStatistiku(rezultati)
        }
    }

    private fun izracunajStatistiku(svi: List<GameResult>): PlayerStats {

        val kzz = statistikaIgre(svi, GameType.KO_ZNA_ZNA, "Pogođenih pitanja") { r ->
            val tacnih = sumDetail(r, "tacnih")
            val ukupno = tacnih + sumDetail(r, "netacnih") + sumDetail(r, "bezOdgovora")
            procenat(tacnih, ukupno)
        }

        val spojnice = statistikaIgre(svi, GameType.SPOJNICE, "Povezanih pojmova") { r ->
            procenat(sumDetail(r, "povezanih"), sumDetail(r, "pokusaja"))
        }

        val asocijacije = statistikaIgre(svi, GameType.ASOCIJACIJE, "Rešenih asocijacija") { r ->
            procenat(sumDetail(r, "resenihFinala"), sumDetail(r, "rundi"))
        }

        val skocko = statistikaIgre(svi, GameType.SKOCKO, "Pogođena kombinacija") { r ->
            procenat(sumDetail(r, "resenihRundi"), sumDetail(r, "rundi"))
        }

        val mojBroj = statistikaIgre(svi, GameType.MOJ_BROJ, "Pronađen tačan broj") { r ->
            procenat(r.count { it.myPoints >= 10 }.toLong(), r.size.toLong())
        }

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
            totalGamesPlayed = svi.size,
            totalWins = svi.count { it.won },
            totalLosses = svi.count { !it.won }
        )
    }

    private fun statistikaIgre(
        svi: List<GameResult>,
        tip: GameType,
        metrikaLabel: String,
        metrika: (List<GameResult>) -> Float
    ): GameStatistic {
        val rezultati = svi.filter { it.gameType == tip.name }

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

    fun changeAvatar(avatarResId: Int) {
        val current = _userProfile.value ?: return
        _userProfile.value = current.copy(avatarResId = avatarResId)

        val avatarId = availableAvatars.indexOf(avatarResId) + 1
        if (avatarId > 0) {
            viewModelScope.launch { runCatching { profilRepo.sacuvajAvatar(avatarId) } }
        }
    }

    fun logout() = authRepo.logout()

    private val _lozinkaPromenjena = MutableLiveData<Boolean?>()
    val lozinkaPromenjena: LiveData<Boolean?> = _lozinkaPromenjena

    private val _lozinkaGreska = MutableLiveData<String?>()
    val lozinkaGreska: LiveData<String?> = _lozinkaGreska

    /** Spec 1.e: reset lozinke unosom stare lozinke i nove lozinke (potvrđene) unutar forme. */
    fun promeniLozinku(staraLozinka: String, novaLozinka: String) {
        viewModelScope.launch {
            runCatching { authRepo.changePassword(staraLozinka, novaLozinka) }
                .onSuccess {
                    _lozinkaGreska.value = null
                    _lozinkaPromenjena.value = true
                }
                .onFailure { e ->
                    _lozinkaGreska.value = e.message ?: "Promjena lozinke nije uspjela."
                    _lozinkaPromenjena.value = false
                }
        }
    }

    fun consumeLozinkaPromenjena() { _lozinkaPromenjena.value = null }

    private fun avatarRes(avatarId: Int): Int =
        availableAvatars.getOrElse(avatarId - 1) { availableAvatars[0] }
}
