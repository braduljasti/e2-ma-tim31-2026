package com.example.slagalica.viewmodel

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.R
import com.example.slagalica.model.SpojniceKonstante
import com.example.slagalica.model.SpojniceRezultat
import com.example.slagalica.model.SpojniceRundaPodaci
import com.example.slagalica.model.SpojniceStanjeCelije
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spojnice - korigovana logika:
 *  - Levi pojmovi se auto-selektuju redom (1, 2, 3, 4, 5)
 *  - Korisnik bira SAMO sa desne kolone
 *  - Tacan spoj -> oba zeleno, trajno do kraja runde
 *  - Netacan spoj -> oba crveno, trajno (bez retry-a, par je "potrosen")
 *  - Posle svakog spoja auto-skok na sledeci levi
 *  - Dalje preskace celu rundu
 */
class SpojniceViewModel : ViewModel() {

    private val runde = listOf(
        SpojniceRundaPodaci(
            kriterijum = "Poveži glavni grad sa državom",
            leviPojmovi = listOf("Pariz", "Tokio", "Madrid", "Atina", "Lisabon"),
            desniPojmovi = listOf("Grčka", "Francuska", "Portugalija", "Japan", "Španija"),
            tacneVeze = mapOf(0 to 1, 1 to 3, 2 to 4, 3 to 0, 4 to 2)
        ),
        SpojniceRundaPodaci(
            kriterijum = "Poveži životinju sa staništem",
            leviPojmovi = listOf("Polarni medved", "Kamila", "Žaba", "Hobotnica", "Sova"),
            desniPojmovi = listOf("Pustinja", "Šuma", "Polarna kapa", "Okean", "Bara"),
            tacneVeze = mapOf(0 to 2, 1 to 0, 2 to 4, 3 to 3, 4 to 1)
        )
    )

    // ============================================================
    // STATE
    // ============================================================

    private val _trenutnaRunda = MutableLiveData(0)
    val trenutnaRunda: LiveData<Int> = _trenutnaRunda

    private val _kriterijum = MutableLiveData("")
    val kriterijum: LiveData<String> = _kriterijum

    private val _leviTekstovi = MutableLiveData<List<String>>(emptyList())
    val leviTekstovi: LiveData<List<String>> = _leviTekstovi

    private val _desniTekstovi = MutableLiveData<List<String>>(emptyList())
    val desniTekstovi: LiveData<List<String>> = _desniTekstovi

    private val _leveStanja = MutableLiveData<List<SpojniceStanjeCelije>>(
        List(5) { SpojniceStanjeCelije.POCETNO }
    )
    val leveStanja: LiveData<List<SpojniceStanjeCelije>> = _leveStanja

    private val _desnaStanja = MutableLiveData<List<SpojniceStanjeCelije>>(
        List(5) { SpojniceStanjeCelije.POCETNO }
    )
    val desnaStanja: LiveData<List<SpojniceStanjeCelije>> = _desnaStanja

    private val _mojiBodovi = MutableLiveData(0)
    val mojiBodovi: LiveData<Int> = _mojiBodovi

    private val _protivnikBodovi = MutableLiveData(0)
    val protivnikBodovi: LiveData<Int> = _protivnikBodovi

    private val _preostaloVreme = MutableLiveData(SpojniceKonstante.VREME_PO_RUNDI_S)
    val preostaloVreme: LiveData<Int> = _preostaloVreme

    private val _timerBojaRes = MutableLiveData(R.color.timer_normalno)
    val timerBojaRes: LiveData<Int> = _timerBojaRes

    private val _krajIgre = MutableLiveData<SpojniceRezultat?>(null)
    val krajIgre: LiveData<SpojniceRezultat?> = _krajIgre

    // ============================================================
    // INTERNAL
    // ============================================================

    private var timer: CountDownTimer? = null
    private var advanceJob: Job? = null
    private var trenutnaRundaPodaci: SpojniceRundaPodaci? = null
    private var aktivanLeviIndex: Int = -1
    private var gameStarted = false
    private var roundEnding = false

    private var mojeVeze = 0
    private var protivnikoVeza = 0

    // ============================================================
    // PUBLIC API
    // ============================================================

    fun startGameIfNeeded() {
        if (gameStarted) return
        gameStarted = true
        loadRound(0, opponentFirst = false)
    }

    fun restart() {
        cleanup()
        gameStarted = true
        roundEnding = false
        mojeVeze = 0
        protivnikoVeza = 0
        _mojiBodovi.value = 0
        _protivnikBodovi.value = 0
        _krajIgre.value = null
        loadRound(0, opponentFirst = false)
    }

    /**
     * Klik na desnu kolonu - jedini nacin korisnika da intereaguje.
     * Levi je auto-selektovan, samo se bira par.
     */
    fun onDesniClick(index: Int) {
        if (roundEnding) return
        if (aktivanLeviIndex < 0) return
        val desnaStanja = _desnaStanja.value ?: return
        if (desnaStanja[index] != SpojniceStanjeCelije.POCETNO) return  // vec zauzet

        val round = trenutnaRundaPodaci ?: return
        val tacanDesni = round.tacneVeze[aktivanLeviIndex]
        val tacno = (tacanDesni == index)

        primeniPokusaj(aktivanLeviIndex, index, tacno)
        advanceToNextLeft()
    }

    fun onSkip() {
        if (roundEnding) return
        endRound()
    }

    // ============================================================
    // INTERNAL LOGIC
    // ============================================================

    private fun loadRound(roundIndex: Int, opponentFirst: Boolean) {
        roundEnding = false
        val round = runde[roundIndex]
        trenutnaRundaPodaci = round
        _trenutnaRunda.value = roundIndex
        _kriterijum.value = round.kriterijum
        _leviTekstovi.value = round.leviPojmovi
        _desniTekstovi.value = round.desniPojmovi
        _leveStanja.value = List(5) { SpojniceStanjeCelije.POCETNO }
        _desnaStanja.value = List(5) { SpojniceStanjeCelije.POCETNO }
        aktivanLeviIndex = -1
        _preostaloVreme.value = SpojniceKonstante.VREME_PO_RUNDI_S
        _timerBojaRes.value = R.color.timer_normalno

        if (opponentFirst) {
            // Runda 2: protivnik prvi povlaci 1-2 spoja
            simulirajProtivnikaPocetak(round)
        }

        // Pronadji prvi POCETNO levi i selektuj ga
        selektujSledeciLevi()
        startTimer()
    }

    private fun selektujSledeciLevi() {
        val left = _leveStanja.value ?: return
        val sledeci = left.indexOfFirst { it == SpojniceStanjeCelije.POCETNO }

        if (sledeci == -1) {
            // Svi su obradjeni - kraj runde
            aktivanLeviIndex = -1
            onRoundEarlyEnd()
            return
        }

        val novaStanja = left.toMutableList()
        novaStanja[sledeci] = SpojniceStanjeCelije.SELEKTOVANA
        _leveStanja.value = novaStanja
        aktivanLeviIndex = sledeci
    }

    private fun primeniPokusaj(leviIdx: Int, desniIdx: Int, tacno: Boolean) {
        val novaLeva = _leveStanja.value!!.toMutableList()
        val novaDesna = _desnaStanja.value!!.toMutableList()

        val finalState = if (tacno)
            SpojniceStanjeCelije.POVEZANA_MOJA_TACNO
        else
            SpojniceStanjeCelije.POVEZANA_MOJA_NETACNO

        novaLeva[leviIdx] = finalState
        novaDesna[desniIdx] = finalState

        _leveStanja.value = novaLeva
        _desnaStanja.value = novaDesna

        if (tacno) {
            _mojiBodovi.value = (_mojiBodovi.value ?: 0) + SpojniceKonstante.BODOVA_PO_VEZI
            mojeVeze++
        }
    }

    private fun advanceToNextLeft() {
        aktivanLeviIndex = -1
        selektujSledeciLevi()
    }

    /**
     * Pre Runde 2: protivnik vec ima 1-2 spoja jer "on prvi povlaci".
     */
    private fun simulirajProtivnikaPocetak(round: SpojniceRundaPodaci) {
        val brojSpojeva = (1..2).random()
        val zaSpoj = round.tacneVeze.keys.shuffled().take(brojSpojeva)
        primeniProtivnikoveSpoje(zaSpoj, round)
    }

    /**
     * Posle isteka tajmera/skip-a: protivnik dobija sansu samo za POCETNO leve
     * pojmove (one koje korisnik nije ni pokusao). Pogresno povezani su trajno
     * crveni i protivnik im se vise ne moze priblizi.
     */
    private fun simulirajProtivnikaKraj() {
        val left = _leveStanja.value ?: return
        val round = trenutnaRundaPodaci ?: return

        // Samo POCETNO i SELEKTOVANA su validni - oni nisu jos pokusani
        val nepovezani = left.indices.filter {
            left[it] == SpojniceStanjeCelije.POCETNO ||
                    left[it] == SpojniceStanjeCelije.SELEKTOVANA
        }
        if (nepovezani.isEmpty()) {
            ocistiSelektovan()
            return
        }

        val brojSpojeva = (0..nepovezani.size).random()
        val zaSpoj = nepovezani.shuffled().take(brojSpojeva)
        primeniProtivnikoveSpoje(zaSpoj, round)
        ocistiSelektovan()
    }

    private fun primeniProtivnikoveSpoje(zaSpoj: List<Int>, round: SpojniceRundaPodaci) {
        val novaLeva = (_leveStanja.value ?: List(5) { SpojniceStanjeCelije.POCETNO }).toMutableList()
        val novaDesna = (_desnaStanja.value ?: List(5) { SpojniceStanjeCelije.POCETNO }).toMutableList()

        var primenjeno = 0
        for (leviIdx in zaSpoj) {
            val desniIdx = round.tacneVeze[leviIdx] ?: continue
            // Ako je tacni desni vec zakljucan (korisnik ga je pogresno koristio za drugi par),
            // protivnik ne moze nista da uradi
            if (novaDesna[desniIdx] != SpojniceStanjeCelije.POCETNO) continue
            novaLeva[leviIdx] = SpojniceStanjeCelije.POVEZANA_PROTIVNIKOVA
            novaDesna[desniIdx] = SpojniceStanjeCelije.POVEZANA_PROTIVNIKOVA
            primenjeno++
        }

        _leveStanja.value = novaLeva
        _desnaStanja.value = novaDesna
        _protivnikBodovi.value = (_protivnikBodovi.value ?: 0) + primenjeno * SpojniceKonstante.BODOVA_PO_VEZI
        protivnikoVeza += primenjeno
    }

    /**
     * Vrati eventualno preostalu SELEKTOVANA u POCETNO (npr. korisnik pritisnuo Dalje
     * dok je levi pojam bio aktivan).
     */
    private fun ocistiSelektovan() {
        val left = _leveStanja.value ?: return
        if (left.none { it == SpojniceStanjeCelije.SELEKTOVANA }) return
        val nova = left.map {
            if (it == SpojniceStanjeCelije.SELEKTOVANA) SpojniceStanjeCelije.POCETNO else it
        }
        _leveStanja.value = nova
    }

    private fun startTimer() {
        timer?.cancel()
        val ukupno = SpojniceKonstante.VREME_PO_RUNDI_S * 1000L
        timer = object : CountDownTimer(ukupno, 200L) {
            override fun onTick(millisUntilFinished: Long) {
                val sekundi = ((millisUntilFinished + 999L) / 1000L).toInt()
                _preostaloVreme.value = sekundi
                _timerBojaRes.value = bojaZaSekundi(sekundi)
            }

            override fun onFinish() {
                _preostaloVreme.value = 0
                endRound()
            }
        }.start()
    }

    private fun bojaZaSekundi(s: Int): Int = when {
        s >= 11 -> R.color.timer_normalno
        s >= 6 -> R.color.timer_upozorenje
        else -> R.color.timer_hitno
    }

    private fun onRoundEarlyEnd() {
        endRound()
    }

    private fun endRound() {
        if (roundEnding) return
        roundEnding = true
        timer?.cancel()
        simulirajProtivnikaKraj()

        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(ROUND_TRANSITION_MS)
            advanceToNext()
        }
    }

    private fun advanceToNext() {
        val sledeci = (_trenutnaRunda.value ?: 0) + 1
        if (sledeci >= runde.size) {
            endGame()
        } else {
            loadRound(sledeci, opponentFirst = true)
        }
    }

    private fun endGame() {
        cleanup()
        _krajIgre.value = SpojniceRezultat(
            mojiBodovi = _mojiBodovi.value ?: 0,
            protivnikBodovi = _protivnikBodovi.value ?: 0,
            mojeVeze = mojeVeze,
            protivnikoVeza = protivnikoVeza
        )
    }

    private fun cleanup() {
        timer?.cancel()
        advanceJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    companion object {
        private const val ROUND_TRANSITION_MS = 1500L
    }
}