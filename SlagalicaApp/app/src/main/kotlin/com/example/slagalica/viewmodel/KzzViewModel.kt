package com.example.slagalica.viewmodel

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.R
import com.example.slagalica.model.KzzKonstante
import com.example.slagalica.model.KzzPitanje
import com.example.slagalica.model.KzzRezultat
import com.example.slagalica.model.KzzStanjePitanja
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel za igru "Ko zna zna".
 *
 * Vodi 5 pitanja, tajmer od 5s po pitanju, bodovanje, i simulaciju
 * protivnika koji moze biti brzi/sporiji ili netacan.
 *
 * Bodovanje (specifikacija):
 *   +10  za tacan odgovor
 *   -5   za netacan
 *    0   za istekao timer ili preskoceno pitanje
 *   Ako oba igraca tacno odgovore, bodove dobija brzi.
 */
class KzzViewModel : ViewModel() {

    // ============================================================
    // PITANJA - hardkodovano (5 pitanja, 4 odgovora svako)
    // ============================================================
    private val pitanja = listOf(
        KzzPitanje(
            tekst = "Koji je glavni grad Australije?",
            odgovori = listOf("Sidnej", "Melburn", "Kanbera", "Pert"),
            tacanIndex = 2
        ),
        KzzPitanje(
            tekst = "Koje godine je počeo Prvi svetski rat?",
            odgovori = listOf("1914", "1918", "1939", "1900"),
            tacanIndex = 0
        ),
        KzzPitanje(
            tekst = "Koliko planeta ima Sunčev sistem?",
            odgovori = listOf("7", "8", "9", "10"),
            tacanIndex = 1
        ),
        KzzPitanje(
            tekst = "Ko je napisao roman 'Na Drini ćuprija'?",
            odgovori = listOf("Miloš Crnjanski", "Ivo Andrić", "Branko Ćopić", "Meša Selimović"),
            tacanIndex = 1
        ),
        KzzPitanje(
            tekst = "Koji je hemijski simbol za zlato?",
            odgovori = listOf("Au", "Ag", "Zl", "Go"),
            tacanIndex = 0
        )
    )

    // ============================================================
    // STATE - LiveData za Fragment
    // ============================================================

    private val _trenutniIndex = MutableLiveData(0)
    val trenutniIndex: LiveData<Int> = _trenutniIndex

    private val _trenutnoPitanje = MutableLiveData<KzzPitanje>()
    val trenutnoPitanje: LiveData<KzzPitanje> = _trenutnoPitanje

    private val _stanje = MutableLiveData(KzzStanjePitanja.AKTIVNO)
    val stanje: LiveData<KzzStanjePitanja> = _stanje

    private val _mojiBodovi = MutableLiveData(0)
    val mojiBodovi: LiveData<Int> = _mojiBodovi

    private val _protivnikBodovi = MutableLiveData(0)
    val protivnikBodovi: LiveData<Int> = _protivnikBodovi

    private val _preostaloVreme = MutableLiveData(KzzKonstante.VREME_PO_PITANJU_S)
    val preostaloVreme: LiveData<Int> = _preostaloVreme

    /** Color resource za pozadinu timera (timer_normalno -> upozorenje -> hitno) */
    private val _timerBojaRes = MutableLiveData(R.color.timer_normalno)
    val timerBojaRes: LiveData<Int> = _timerBojaRes

    /** Indeks koji je korisnik kliknuo (-1 ako nije). Za vizuelni feedback. */
    private val _odabranIndex = MutableLiveData(-1)
    val odabranIndex: LiveData<Int> = _odabranIndex

    /** Kraj igre - != null prikazuje finalni dijalog */
    private val _krajIgre = MutableLiveData<KzzRezultat?>(null)
    val krajIgre: LiveData<KzzRezultat?> = _krajIgre

    // ============================================================
    // INTERNAL STATE
    // ============================================================

    private var timer: CountDownTimer? = null
    private var pitanjeStartedAt: Long = 0L
    private var trenutniProtivnik = ProtivnikRound(false, false, Long.MAX_VALUE)
    private var advanceJob: Job? = null
    private var gameStarted = false

    private var brojTacnih = 0
    private var brojNetacnih = 0
    private var brojPromasenih = 0

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Poziva se iz Fragmenta u onViewCreated. Flag sprecava da se igra
     * resetuje kad se ViewModel reattach-uje (npr. posle rotacije).
     */
    fun startGameIfNeeded() {
        if (gameStarted) return
        gameStarted = true
        loadQuestion(0)
    }

    fun restart() {
        cleanup()
        gameStarted = true
        brojTacnih = 0
        brojNetacnih = 0
        brojPromasenih = 0
        _mojiBodovi.value = 0
        _protivnikBodovi.value = 0
        _krajIgre.value = null
        loadQuestion(0)
    }

    fun onAnswerSelected(index: Int) {
        if (_stanje.value != KzzStanjePitanja.AKTIVNO) return
        timer?.cancel()

        val korisnikVreme = System.currentTimeMillis() - pitanjeStartedAt
        val tacanIdx = _trenutnoPitanje.value?.tacanIndex ?: return
        val korisnikTacan = index == tacanIdx

        _odabranIndex.value = index
        _stanje.value = KzzStanjePitanja.ODGOVORENO

        resolveRound(korisnikTacan, korisnikVreme)
        scheduleNext(FEEDBACK_DELAY_MS)
    }

    fun onSkip() {
        if (_stanje.value != KzzStanjePitanja.AKTIVNO) return
        timer?.cancel()
        brojPromasenih++

        // Protivnik nezavisno odgovara - ne menja se status korisnika
        resolveProtivnik(korisnikBrziTacan = false)
        scheduleNext(0L)  // bez feedback delay-a
    }

    // ============================================================
    // INTERNAL LOGIC
    // ============================================================

    private fun loadQuestion(index: Int) {
        _trenutniIndex.value = index
        _trenutnoPitanje.value = pitanja[index]
        _stanje.value = KzzStanjePitanja.AKTIVNO
        _odabranIndex.value = -1
        _preostaloVreme.value = KzzKonstante.VREME_PO_PITANJU_S
        _timerBojaRes.value = R.color.timer_normalno

        trenutniProtivnik = generisiProtivnika()
        pitanjeStartedAt = System.currentTimeMillis()
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        val ukupno = KzzKonstante.VREME_PO_PITANJU_S * 1000L
        timer = object : CountDownTimer(ukupno, 200L) {
            override fun onTick(millisUntilFinished: Long) {
                // Ceiling: kad je 4900ms preostalo, prikazi "5", a ne "4"
                val sekundi = ((millisUntilFinished + 999L) / 1000L).toInt()
                _preostaloVreme.value = sekundi
                _timerBojaRes.value = bojaZaSekundi(sekundi)
            }

            override fun onFinish() {
                _preostaloVreme.value = 0
                onTimeExpired()
            }
        }.start()
    }

    private fun bojaZaSekundi(s: Int): Int = when {
        s >= 4 -> R.color.timer_normalno
        s >= 2 -> R.color.timer_upozorenje
        else -> R.color.timer_hitno
    }

    private fun onTimeExpired() {
        if (_stanje.value != KzzStanjePitanja.AKTIVNO) return
        _stanje.value = KzzStanjePitanja.ISTEKLO
        brojPromasenih++

        resolveProtivnik(korisnikBrziTacan = false)
        scheduleNext(FEEDBACK_DELAY_MS)
    }

    private fun resolveRound(korisnikTacan: Boolean, korisnikVreme: Long) {
        // Bodovi korisnika
        if (korisnikTacan) {
            // +10 osim ako je protivnik bio brzi i tacan
            val protivnikBrziTacan = trenutniProtivnik.odgovorio &&
                    trenutniProtivnik.tacan &&
                    trenutniProtivnik.vreme < korisnikVreme
            if (!protivnikBrziTacan) {
                _mojiBodovi.value = mojiBodovi() + KzzKonstante.BODOVA_ZA_TACAN
            }
            brojTacnih++
        } else {
            _mojiBodovi.value = mojiBodovi() + KzzKonstante.BODOVA_ZA_NETACAN
            brojNetacnih++
        }

        // Protivnik
        val korisnikBrziTacan = korisnikTacan && korisnikVreme < trenutniProtivnik.vreme
        resolveProtivnik(korisnikBrziTacan)
    }

    private fun resolveProtivnik(korisnikBrziTacan: Boolean) {
        if (!trenutniProtivnik.odgovorio) return
        if (trenutniProtivnik.tacan) {
            if (!korisnikBrziTacan) {
                _protivnikBodovi.value = protivnikBodovi() + KzzKonstante.BODOVA_ZA_TACAN
            }
        } else {
            _protivnikBodovi.value = protivnikBodovi() + KzzKonstante.BODOVA_ZA_NETACAN
        }
    }

    private fun generisiProtivnika(): ProtivnikRound {
        val odgovorio = (1..10).random() <= 8           // 80% sansi da odgovori
        val tacan = odgovorio && (1..10).random() <= 7  // 70% sansi tacan (ako je odgovorio)
        val vreme = if (odgovorio) (1500..5000).random().toLong() else Long.MAX_VALUE
        return ProtivnikRound(odgovorio, tacan, vreme)
    }

    private fun scheduleNext(delayMs: Long) {
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            advanceToNext()
        }
    }

    private fun advanceToNext() {
        val sledeci = (_trenutniIndex.value ?: 0) + 1
        if (sledeci >= pitanja.size) {
            endGame()
        } else {
            loadQuestion(sledeci)
        }
    }

    private fun endGame() {
        cleanup()
        _krajIgre.value = KzzRezultat(
            mojiBodovi = mojiBodovi(),
            protivnikBodovi = protivnikBodovi(),
            mojiTacni = brojTacnih,
            mojiNetacni = brojNetacnih,
            mojiPromaseni = brojPromasenih
        )
    }

    private fun cleanup() {
        timer?.cancel()
        advanceJob?.cancel()
    }

    private fun mojiBodovi() = _mojiBodovi.value ?: 0
    private fun protivnikBodovi() = _protivnikBodovi.value ?: 0

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    /**
     * Simulacija jedne runde za protivnika - generise se na pocetku svakog pitanja.
     * Posto nemamo backend ni drugi uredjaj, izmisljamo "kakav bi bio" protivnik.
     */
    private data class ProtivnikRound(
        val odgovorio: Boolean,
        val tacan: Boolean,
        val vreme: Long  // ms od pocetka pitanja, MAX_VALUE = nije odgovorio
    )

    companion object {
        private const val FEEDBACK_DELAY_MS = 1500L
    }
}