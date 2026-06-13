package com.example.slagalica.viewmodel

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.R
import com.example.slagalica.model.AsocijacijaCelijaStanje
import com.example.slagalica.model.AsocijacijeKonstante
import com.example.slagalica.model.AsocijacijeRezultat
import com.example.slagalica.model.AsocijacijeRundaPodaci
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AsocijacijeViewModel : ViewModel() {

    private val runde = listOf(
        AsocijacijeRundaPodaci(
            polja = listOf(
                listOf("Hrast", "Šuma", "List", "Koren"),
                listOf("Zraci", "Leto", "Žuto", "Ujutru"),
                listOf("Kutija", "Glava", "Paliti", "Sumporna"),
                listOf("Krv", "Ruža", "Jabuka", "Ferari")
            ),
            resenjaKolona = listOf("DRVO", "SUNCE", "ŠIBICA", "CRVENO"),
            finalnoResenje = "VATRA"
        ),
        AsocijacijeRundaPodaci(
            polja = listOf(
                listOf("Žica", "Akord", "Akustična", "Prsti"),
                listOf("Stih", "Refren", "Melodija", "Ritam"),
                listOf("Bina", "Publika", "Ulaznica", "Ovacije"),
                listOf("Linija", "Pauza", "Čitanje", "Ključ")
            ),
            resenjaKolona = listOf("GITARA", "PESMA", "KONCERT", "NOTA"),
            finalnoResenje = "MUZIKA"
        )
    )

    private val _trenutnaRunda = MutableLiveData(0)
    val trenutnaRunda: LiveData<Int> = _trenutnaRunda

    private val _tekstoviPolja = MutableLiveData<List<List<String>>>(emptyList())
    val tekstoviPolja: LiveData<List<List<String>>> = _tekstoviPolja

    private val _stanjaPolja = MutableLiveData<List<List<AsocijacijaCelijaStanje>>>(
        List(4) { List(4) { AsocijacijaCelijaStanje.ZAKLJUCANO } }
    )
    val stanjaPolja: LiveData<List<List<AsocijacijaCelijaStanje>>> = _stanjaPolja

    private val _tekstoviResenjaKolona = MutableLiveData<List<String>>(emptyList())
    val tekstoviResenjaKolona: LiveData<List<String>> = _tekstoviResenjaKolona

    private val _stanjaResenjaKolona = MutableLiveData<List<AsocijacijaCelijaStanje>>(
        List(4) { AsocijacijaCelijaStanje.ZAKLJUCANO }
    )
    val stanjaResenjaKolona: LiveData<List<AsocijacijaCelijaStanje>> = _stanjaResenjaKolona

    private val _tekstFinalno = MutableLiveData("")
    val tekstFinalno: LiveData<String> = _tekstFinalno

    private val _stanjeFinalno = MutableLiveData(AsocijacijaCelijaStanje.ZAKLJUCANO)
    val stanjeFinalno: LiveData<AsocijacijaCelijaStanje> = _stanjeFinalno

    private val _mojiBodovi = MutableLiveData(0)
    val mojiBodovi: LiveData<Int> = _mojiBodovi

    private val _protivnikBodovi = MutableLiveData(0)
    val protivnikBodovi: LiveData<Int> = _protivnikBodovi

    private val _preostaloVreme = MutableLiveData(AsocijacijeKonstante.VREME_PO_RUNDI_S)
    val preostaloVreme: LiveData<Int> = _preostaloVreme

    private val _timerBojaRes = MutableLiveData(R.color.timer_normalno)
    val timerBojaRes: LiveData<Int> = _timerBojaRes

    private val _krajIgre = MutableLiveData<AsocijacijeRezultat?>(null)
    val krajIgre: LiveData<AsocijacijeRezultat?> = _krajIgre

    private var timer: CountDownTimer? = null
    private var advanceJob: Job? = null
    private var trenutnaRundaPodaci: AsocijacijeRundaPodaci? = null
    private var gameStarted = false
    private var roundEnding = false
    private var mojeResenja = 0
    private var protivnikoveResenja = 0

    fun startGameIfNeeded() {
        if (gameStarted) return
        gameStarted = true
        loadRound(0)
    }

    fun restart() {
        cleanup()
        gameStarted = true
        roundEnding = false
        mojeResenja = 0
        protivnikoveResenja = 0
        _mojiBodovi.value = 0
        _protivnikBodovi.value = 0
        _krajIgre.value = null
        loadRound(0)
    }

    fun onPoljeClick(columnIdx: Int, rowIdx: Int) {
        if (roundEnding) return
        val stanja = _stanjaPolja.value ?: return
        if (stanja[columnIdx][rowIdx] != AsocijacijaCelijaStanje.ZAKLJUCANO) return

        val nova = stanja.map { it.toMutableList() }
        nova[columnIdx][rowIdx] = AsocijacijaCelijaStanje.OTKRIVENO
        _stanjaPolja.value = nova.map { it.toList() }
    }

    fun onColumnGuessSubmitted(columnIdx: Int, guess: String): Pair<Boolean, Int> {
        if (roundEnding) return false to 0
        val round = trenutnaRundaPodaci ?: return false to 0
        val stanja = _stanjaResenjaKolona.value ?: return false to 0
        if (stanja[columnIdx] != AsocijacijaCelijaStanje.ZAKLJUCANO) return false to 0

        if (!compareGuess(guess, round.resenjaKolona[columnIdx])) {
            return false to 0
        }

        val score = scoreZaKolonu(columnIdx)
        _mojiBodovi.value = (_mojiBodovi.value ?: 0) + score
        mojeResenja++

        val nova = stanja.toMutableList()
        nova[columnIdx] = AsocijacijaCelijaStanje.POGODENO_MOJE
        _stanjaResenjaKolona.value = nova
        return true to score
    }

    fun onFinalGuessSubmitted(guess: String): Pair<Boolean, Int> {
        if (roundEnding) return false to 0
        val round = trenutnaRundaPodaci ?: return false to 0
        if (_stanjeFinalno.value != AsocijacijaCelijaStanje.ZAKLJUCANO) return false to 0

        if (!compareGuess(guess, round.finalnoResenje)) {
            return false to 0
        }

        val score = scoreZaFinalno()
        _mojiBodovi.value = (_mojiBodovi.value ?: 0) + score
        mojeResenja++
        _stanjeFinalno.value = AsocijacijaCelijaStanje.POGODENO_MOJE

        endRound()
        return true to score
    }

    fun onSkip() {
        if (roundEnding) return
        endRound()
    }

    fun canGuessColumn(columnIdx: Int): Boolean {
        if (roundEnding) return false
        val stanja = _stanjaResenjaKolona.value ?: return false
        return stanja[columnIdx] == AsocijacijaCelijaStanje.ZAKLJUCANO &&
                totalOpenedFields() > 0
    }

    fun canGuessFinal(): Boolean {
        if (roundEnding) return false
        return _stanjeFinalno.value == AsocijacijaCelijaStanje.ZAKLJUCANO &&
                totalOpenedFields() > 0
    }

    fun totalOpenedFields(): Int {
        val s = _stanjaPolja.value ?: return 0
        return s.sumOf { col -> col.count { it == AsocijacijaCelijaStanje.OTKRIVENO } }
    }

    private fun scoreZaKolonu(columnIdx: Int): Int {
        val opened = countOpenedInColumn(columnIdx)
        return AsocijacijeKonstante.KOLONA_BAZA +
                (AsocijacijeKonstante.POLJA_PO_KOLONI - opened) *
                AsocijacijeKonstante.BODOVI_PO_NEOTVORENOM
    }

    private fun scoreZaFinalno(): Int {
        var score = AsocijacijeKonstante.FINALNO_BAZA
        val resenja = _stanjaResenjaKolona.value ?: return score
        for (col in 0..3) {
            if (resenja[col] == AsocijacijaCelijaStanje.ZAKLJUCANO) {
                val opened = countOpenedInColumn(col)
                score += AsocijacijeKonstante.KOLONA_BAZA +
                        (AsocijacijeKonstante.POLJA_PO_KOLONI - opened)
            }
        }
        return score
    }

    private fun countOpenedInColumn(columnIdx: Int): Int {
        val s = _stanjaPolja.value ?: return 0
        return s[columnIdx].count { it == AsocijacijaCelijaStanje.OTKRIVENO }
    }

    private fun compareGuess(guess: String, correct: String): Boolean {
        return guess.trim().equals(correct.trim(), ignoreCase = true)
    }

    private fun loadRound(roundIndex: Int) {
        roundEnding = false
        val round = runde[roundIndex]
        trenutnaRundaPodaci = round
        _trenutnaRunda.value = roundIndex
        _tekstoviPolja.value = round.polja
        _tekstoviResenjaKolona.value = round.resenjaKolona
        _tekstFinalno.value = round.finalnoResenje
        _stanjaPolja.value = List(4) { List(4) { AsocijacijaCelijaStanje.ZAKLJUCANO } }
        _stanjaResenjaKolona.value = List(4) { AsocijacijaCelijaStanje.ZAKLJUCANO }
        _stanjeFinalno.value = AsocijacijaCelijaStanje.ZAKLJUCANO
        _preostaloVreme.value = AsocijacijeKonstante.VREME_PO_RUNDI_S
        _timerBojaRes.value = R.color.timer_normalno

        if (roundIndex == 1) {
            val randomCol = (0..3).random()
            val score = AsocijacijeKonstante.KOLONA_BAZA +
                    AsocijacijeKonstante.POLJA_PO_KOLONI
            val nova = _stanjaResenjaKolona.value!!.toMutableList()
            nova[randomCol] = AsocijacijaCelijaStanje.POGODENO_PROTIVNIK
            _stanjaResenjaKolona.value = nova
            _protivnikBodovi.value = (_protivnikBodovi.value ?: 0) + score
            protivnikoveResenja++
        }

        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        val ukupno = AsocijacijeKonstante.VREME_PO_RUNDI_S * 1000L
        timer = object : CountDownTimer(ukupno, 1000L) {
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
        s >= 30 -> R.color.timer_normalno
        s >= 15 -> R.color.timer_upozorenje
        else -> R.color.timer_hitno
    }

    private fun endRound() {
        if (roundEnding) return
        roundEnding = true
        timer?.cancel()
        simulirajProtivnika()
        revealUnsolved()

        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(ROUND_TRANSITION_MS)
            advanceToNext()
        }
    }

    private fun simulirajProtivnika() {
        val resenja = _stanjaResenjaKolona.value ?: return
        val nova = resenja.toMutableList()
        var dodatoBodova = 0

        for (col in 0..3) {
            if (nova[col] == AsocijacijaCelijaStanje.ZAKLJUCANO) {
                if ((1..100).random() <= 35) {
                    dodatoBodova += scoreZaKolonu(col)
                    protivnikoveResenja++
                    nova[col] = AsocijacijaCelijaStanje.POGODENO_PROTIVNIK
                }
            }
        }
        _stanjaResenjaKolona.value = nova

        if (_stanjeFinalno.value == AsocijacijaCelijaStanje.ZAKLJUCANO) {
            if ((1..100).random() <= 15) {

                var finalScore = AsocijacijeKonstante.FINALNO_BAZA
                for (col in 0..3) {
                    if (nova[col] == AsocijacijaCelijaStanje.ZAKLJUCANO) {
                        val opened = countOpenedInColumn(col)
                        finalScore += AsocijacijeKonstante.KOLONA_BAZA +
                                (AsocijacijeKonstante.POLJA_PO_KOLONI - opened)
                    }
                }
                dodatoBodova += finalScore
                protivnikoveResenja++
                _stanjeFinalno.value = AsocijacijaCelijaStanje.POGODENO_PROTIVNIK
            }
        }

        if (dodatoBodova > 0) {
            _protivnikBodovi.value = (_protivnikBodovi.value ?: 0) + dodatoBodova
        }
    }

    private fun revealUnsolved() {
        val novaPolja = _stanjaPolja.value!!.map { col ->
            col.map {
                if (it == AsocijacijaCelijaStanje.ZAKLJUCANO)
                    AsocijacijaCelijaStanje.OTKRIVENO
                else it
            }
        }
        _stanjaPolja.value = novaPolja

        val novaResenja = _stanjaResenjaKolona.value!!.map {
            if (it == AsocijacijaCelijaStanje.ZAKLJUCANO)
                AsocijacijaCelijaStanje.OTKRIVENO
            else it
        }
        _stanjaResenjaKolona.value = novaResenja

        if (_stanjeFinalno.value == AsocijacijaCelijaStanje.ZAKLJUCANO) {
            _stanjeFinalno.value = AsocijacijaCelijaStanje.OTKRIVENO
        }
    }

    private fun advanceToNext() {
        val sledeci = (_trenutnaRunda.value ?: 0) + 1
        if (sledeci >= runde.size) endGame() else loadRound(sledeci)
    }

    private fun endGame() {
        cleanup()
        _krajIgre.value = AsocijacijeRezultat(
            mojiBodovi = _mojiBodovi.value ?: 0,
            protivnikBodovi = _protivnikBodovi.value ?: 0,
            mojeResenja = mojeResenja,
            protivnikoveResenja = protivnikoveResenja
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
        private const val ROUND_TRANSITION_MS = 2500L
    }
}
