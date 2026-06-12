package com.example.slagalica.ui.games

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.slagalica.R
import com.example.slagalica.data.GameLogic
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.databinding.DialogAsocijacijeUnosBinding
import com.example.slagalica.databinding.FragmentAsocijacijeBinding
import com.example.slagalica.model.AsocijacijaCelijaStanje
import com.example.slagalica.model.AsocijacijeKonstante
import com.example.slagalica.model.MatchState
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Multiplayer Asocijacije: za razliku od KZZ i Spojnica, tabla je ZAJEDNIČKA
 * i živa - igrači se smenjuju potez po potez. Svaki potez (otvori polje,
 * pogodi kolonu/finalno, propusti) ide odmah u Firestore kao transakcija,
 * a oba telefona crtaju tablu isključivo iz stanja meča (jedan izvor istine).
 *
 * Pravila po specifikaciji: na svom potezu igrač otvara polje, pa može da
 * pogađa rešenje kolone ili konačno rešenje; dok pogađa tačno - nastavlja;
 * na grešku (ili "propusti") potez prelazi protivniku. Runda se završava
 * pogotkom konačnog rešenja ili istekom 2 minuta.
 */
class AsocijacijeMpFragment : Fragment() {

    private var _binding: FragmentAsocijacijeBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel

    private lateinit var poljeKartice: List<List<MaterialCardView>>
    private lateinit var poljeTekstovi: List<List<TextView>>
    private lateinit var resenjeKartice: List<MaterialCardView>
    private lateinit var resenjeTekstovi: List<TextView>

    private var playedRoundIndex = -1
    private var timer: CountDownTimer? = null
    private var finalShown = false
    private var lastState: MatchState? = null

    // Pauza na kraju runde (otkrivena tabla) + dedup za prikaz tuđih pokušaja
    private var advanceScheduled = false
    private var advanceJob: Job? = null
    private var lastPokusajTs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAsocijacijeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]
        setupBindings()
        setupClickListeners()
        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    private fun setupBindings() {
        poljeKartice = listOf(
            listOf(binding.cellA1.root, binding.cellA2.root, binding.cellA3.root, binding.cellA4.root),
            listOf(binding.cellB1.root, binding.cellB2.root, binding.cellB3.root, binding.cellB4.root),
            listOf(binding.cellC1.root, binding.cellC2.root, binding.cellC3.root, binding.cellC4.root),
            listOf(binding.cellD1.root, binding.cellD2.root, binding.cellD3.root, binding.cellD4.root)
        )
        poljeTekstovi = listOf(
            listOf(binding.cellA1.tvAsocCelija, binding.cellA2.tvAsocCelija,
                binding.cellA3.tvAsocCelija, binding.cellA4.tvAsocCelija),
            listOf(binding.cellB1.tvAsocCelija, binding.cellB2.tvAsocCelija,
                binding.cellB3.tvAsocCelija, binding.cellB4.tvAsocCelija),
            listOf(binding.cellC1.tvAsocCelija, binding.cellC2.tvAsocCelija,
                binding.cellC3.tvAsocCelija, binding.cellC4.tvAsocCelija),
            listOf(binding.cellD1.tvAsocCelija, binding.cellD2.tvAsocCelija,
                binding.cellD3.tvAsocCelija, binding.cellD4.tvAsocCelija)
        )
        resenjeKartice = listOf(
            binding.resenjeA.root, binding.resenjeB.root,
            binding.resenjeC.root, binding.resenjeD.root
        )
        resenjeTekstovi = listOf(
            binding.resenjeA.tvAsocCelija, binding.resenjeB.tvAsocCelija,
            binding.resenjeC.tvAsocCelija, binding.resenjeD.tvAsocCelija
        )
    }

    private fun setupClickListeners() {
        for (col in 0..3) {
            for (row in 0..3) {
                poljeKartice[col][row].setOnClickListener { onPoljeClick(col, row) }
            }
        }
        resenjeKartice.forEachIndexed { col, kartica ->
            kartica.setOnClickListener { onResenjeKoloneClick(col) }
        }
        binding.resenjeFinalno.root.setOnClickListener { onFinalnoClick() }
        binding.btnDaljeAsoc.text = getString(R.string.mp_asoc_propusti)
        binding.btnDaljeAsoc.setOnClickListener { onPropusti() }
    }

    // ============================================================
    // SINHRONIZACIJA SA MEČOM
    // ============================================================

    private fun onMatchUpdate(state: MatchState) {
        lastState = state

        // Skor uživo: bodovi se upisuju u rundu u trenutku pogotka
        val isP1 = state.isPlayer1(mp.uid)
        binding.scoreboardAsoc.tvMojiBodovi.text =
            state.rounds.sumOf { if (isP1) it.p1Points else it.p2Points }.toString()
        binding.scoreboardAsoc.tvProtivnikBodovi.text =
            state.rounds.sumOf { if (isP1) it.p2Points else it.p1Points }.toString()

        if (state.finished) { showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != MultiplayerRepository.GAME_ASOCIJACIJE) return

        // Nova runda -> restart lokalnog tajmera (2 min)
        if (state.currentRoundIndex != playedRoundIndex) {
            playedRoundIndex = state.currentRoundIndex
            advanceScheduled = false
            advanceJob?.cancel()
            startTimer()
        }

        renderBoard(state)
        prikaziProtivnikovPokusaj(state)

        // Kraj runde: tabla je otkrivena, 7 sekundi pregleda pa meč ide dalje.
        // Oba klijenta zakazuju - prva transakcija pobedi, druga tiho odustane.
        if (round.asocZavrsena() && !advanceScheduled) {
            advanceScheduled = true
            timer?.cancel()
            binding.tvTimerAsoc.text = "–"
            binding.cardTimerAsoc.setCardBackgroundColor(boja(R.color.timer_normalno))
            advanceJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(REVEAL_PAUSE_MS)
                mp.asocijacijeSledecaRunda()
            }
        }
    }

    /** Prikazuje protivnikov pokušaj pogađanja (Snackbar, ~3 sekunde). */
    private fun prikaziProtivnikovPokusaj(state: MatchState) {
        val pokusaj = state.currentRound?.asocPoslednjiPokusaj() ?: return
        val uid = pokusaj["uid"] as? String ?: return
        val ts = (pokusaj["ts"] as? Number)?.toLong() ?: return
        if (uid == mp.uid || ts == lastPokusajTs) return   // moj pokušaj / već prikazan
        lastPokusajTs = ts
        if (System.currentTimeMillis() - ts > 10_000) return   // star pokušaj (npr. posle rotacije)

        val cilj = pokusaj["cilj"] as? String ?: return
        val tekst = pokusaj["tekst"] as? String ?: ""
        val tacno = pokusaj["tacno"] == true
        val poruka = if (cilj == "F") {
            getString(R.string.mp_asoc_pokusaj_finalno, tekst)
        } else {
            getString(R.string.mp_asoc_pokusaj_kolona, cilj, tekst)
        } + if (tacno) " ✓" else " ✗"

        Snackbar.make(binding.root, poruka, Snackbar.LENGTH_INDEFINITE)
            .setDuration(POKUSAJ_PRIKAZ_MS)
            .show()
    }

    /** Crta celu tablu iz stanja meča - tabla je ista na oba telefona. */
    private fun renderBoard(state: MatchState) {
        val round = state.currentRound ?: return
        val runda = round.asocRunda() ?: return
        val otvorena = round.asocOtvorena().toSet()
        val resene = round.asocReseneKolone()
        val finalnoUid = round.asocResenoFinalnoUid()

        for (col in 0..3) {
            for (row in 0..3) {
                val stanje = if (col to row in otvorena) AsocijacijaCelijaStanje.OTKRIVENO
                else AsocijacijaCelijaStanje.ZAKLJUCANO
                stilirajPolje(
                    poljeKartice[col][row], poljeTekstovi[col][row],
                    stanje, runda.polja[col][row], "${"ABCD"[col]}${row + 1}"
                )
            }
        }
        val zavrsena = round.asocZavrsena()
        for (col in 0..3) {
            val resioUid = resene[col]
            val stanje = when {
                resioUid == mp.uid -> AsocijacijaCelijaStanje.POGODENO_MOJE
                resioUid != null -> AsocijacijaCelijaStanje.POGODENO_PROTIVNIK
                zavrsena -> AsocijacijaCelijaStanje.OTKRIVENO   // niko nije rešio - sivo
                else -> AsocijacijaCelijaStanje.ZAKLJUCANO
            }
            stilirajResenje(
                resenjeKartice[col], resenjeTekstovi[col],
                stanje, runda.resenjaKolona[col], "${"ABCD"[col]}"
            )
        }
        val finalnoStanje = when {
            finalnoUid == mp.uid -> AsocijacijaCelijaStanje.POGODENO_MOJE
            finalnoUid != null -> AsocijacijaCelijaStanje.POGODENO_PROTIVNIK
            zavrsena -> AsocijacijaCelijaStanje.OTKRIVENO
            else -> AsocijacijaCelijaStanje.ZAKLJUCANO
        }
        stilirajResenje(
            binding.resenjeFinalno.root, binding.resenjeFinalno.tvAsocCelija,
            finalnoStanje, runda.finalnoResenje, getString(R.string.asoc_finalno_zakljucano)
        )

        // Runda + status poteza u jednom redu
        val mojPotez = round.asocTurnUid() == mp.uid
        val status = when {
            zavrsena -> getString(R.string.mp_asoc_kraj_runde)
            !mojPotez -> getString(R.string.mp_asoc_protivnik)
            round.asocMozeDaPogadja() -> getString(R.string.mp_asoc_pogadjaj)
            else -> getString(R.string.mp_asoc_tvoj_potez)
        }
        binding.tvBrojRundeAsoc.text = getString(
            R.string.asoc_runda_format, round.roundNumber, AsocijacijeKonstante.BROJ_RUNDI
        ) + " • " + status
        binding.progressRundeAsoc.progress =
            (round.roundNumber * 100) / AsocijacijeKonstante.BROJ_RUNDI
        binding.btnDaljeAsoc.isEnabled = mojPotez && !zavrsena
    }

    // ============================================================
    // POTEZI
    // ============================================================

    private fun onPoljeClick(col: Int, row: Int) {
        val round = lastState?.currentRound ?: return
        if (round.asocTurnUid() != mp.uid) {
            Toast.makeText(requireContext(), R.string.mp_nije_tvoj_potez, Toast.LENGTH_SHORT).show()
            return
        }
        // Na svom potezu igrač otvara JEDNO polje; ako već može da pogađa,
        // sledeće polje dobija tek u narednom potezu
        if (round.asocMozeDaPogadja()) return
        mp.asocijacijeOtvoriPolje(col, row)
    }

    private fun onResenjeKoloneClick(col: Int) {
        val round = lastState?.currentRound ?: return
        val runda = round.asocRunda() ?: return
        if (!smemDaPogadjam(round.asocTurnUid(), round.asocMozeDaPogadja())) return
        if (round.asocReseneKolone().containsKey(col)) return

        val colLetter = "ABCD"[col].toString()
        showGuessDialog(getString(R.string.asoc_pogadjanje_kolone, colLetter)) { unos ->
            // Lokalna provera za momentalni feedback; server svejedno validira
            if (GameLogic.asocijacijeTacno(unos, runda.resenjaKolona[col])) {
                val otvorenihUKoloni = lastState?.currentRound?.asocOtvorena()
                    ?.count { it.first == col } ?: 0
                Toast.makeText(requireContext(),
                    getString(R.string.asoc_tacno_kolona, GameLogic.asocijacijePoeniKolona(otvorenihUKoloni)),
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.asoc_netacno, Toast.LENGTH_SHORT).show()
            }
            mp.asocijacijePogodiKolonu(col, unos)
        }
    }

    private fun onFinalnoClick() {
        val round = lastState?.currentRound ?: return
        val runda = round.asocRunda() ?: return
        if (!smemDaPogadjam(round.asocTurnUid(), round.asocMozeDaPogadja())) return
        if (round.asocResenoFinalnoUid() != null) return

        showGuessDialog(getString(R.string.asoc_pogadjanje_finalno)) { unos ->
            if (GameLogic.asocijacijeTacno(unos, runda.finalnoResenje)) {
                Toast.makeText(requireContext(), R.string.mp_asoc_finalno_tacno, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), R.string.asoc_netacno, Toast.LENGTH_SHORT).show()
            }
            mp.asocijacijePogodiFinalno(unos)
        }
    }

    private fun onPropusti() {
        val round = lastState?.currentRound ?: return
        if (round.asocTurnUid() != mp.uid) return
        mp.asocijacijePropusti()
    }

    private fun smemDaPogadjam(turnUid: String, mozeDaPogadja: Boolean): Boolean {
        if (turnUid != mp.uid) {
            Toast.makeText(requireContext(), R.string.mp_nije_tvoj_potez, Toast.LENGTH_SHORT).show()
            return false
        }
        if (!mozeDaPogadja) {
            Toast.makeText(requireContext(), R.string.asoc_otvori_polje, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showGuessDialog(title: String, onSubmit: (String) -> Unit) {
        val dialogBinding = DialogAsocijacijeUnosBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.asoc_potvrdi) { _, _ ->
                onSubmit(dialogBinding.etAsocUnos.text?.toString() ?: "")
            }
            .setNegativeButton(R.string.asoc_otkazi, null)
            .show()
    }

    // ============================================================
    // TAJMER RUNDE (2 min) - na istek bilo koji klijent zatvara rundu
    // ============================================================

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(AsocijacijeKonstante.VREME_PO_RUNDI_S * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sekundi = ((millisUntilFinished + 999L) / 1000L).toInt()
                binding.tvTimerAsoc.text = sekundi.toString()
                binding.cardTimerAsoc.setCardBackgroundColor(boja(when {
                    sekundi >= 30 -> R.color.timer_normalno
                    sekundi >= 15 -> R.color.timer_upozorenje
                    else -> R.color.timer_hitno
                }))
            }

            override fun onFinish() {
                binding.tvTimerAsoc.text = "0"
                mp.asocijacijeIstekloVreme()
            }
        }.start()
    }

    // ============================================================
    // STILIZACIJA (kao u AsocijacijeFragment)
    // ============================================================

    private fun stilirajPolje(
        kartica: MaterialCardView, tekst: TextView,
        stanje: AsocijacijaCelijaStanje, sadrzaj: String, labelaZakljucano: String
    ) {
        when (stanje) {
            AsocijacijaCelijaStanje.ZAKLJUCANO -> {
                kartica.setCardBackgroundColor(boja(R.color.surface))
                kartica.strokeColor = boja(R.color.primary_light)
                tekst.text = labelaZakljucano
                tekst.setTextColor(boja(R.color.text_primary))
                kartica.isClickable = true
            }
            else -> {
                kartica.setCardBackgroundColor(boja(R.color.primary_light))
                kartica.strokeColor = boja(R.color.primary)
                tekst.text = sadrzaj
                tekst.setTextColor(boja(R.color.white))
                kartica.isClickable = false
            }
        }
    }

    private fun stilirajResenje(
        kartica: MaterialCardView, tekst: TextView,
        stanje: AsocijacijaCelijaStanje, sadrzaj: String, labelaZakljucano: String
    ) {
        when (stanje) {
            AsocijacijaCelijaStanje.ZAKLJUCANO -> {
                kartica.setCardBackgroundColor(boja(R.color.accent))
                kartica.strokeColor = boja(R.color.accent_dark)
                tekst.text = labelaZakljucano
                tekst.setTextColor(boja(R.color.primary_dark))
                kartica.isClickable = true
            }
            AsocijacijaCelijaStanje.OTKRIVENO -> {
                kartica.setCardBackgroundColor(boja(R.color.text_secondary))
                kartica.strokeColor = boja(R.color.text_secondary)
                tekst.text = sadrzaj
                tekst.setTextColor(boja(R.color.white))
                kartica.isClickable = false
            }
            AsocijacijaCelijaStanje.POGODENO_MOJE -> {
                kartica.setCardBackgroundColor(boja(R.color.success))
                kartica.strokeColor = boja(R.color.success)
                tekst.text = sadrzaj
                tekst.setTextColor(boja(R.color.white))
                kartica.isClickable = false
            }
            AsocijacijaCelijaStanje.POGODENO_PROTIVNIK -> {
                kartica.setCardBackgroundColor(boja(R.color.info))
                kartica.strokeColor = boja(R.color.info)
                tekst.text = sadrzaj
                tekst.setTextColor(boja(R.color.white))
                kartica.isClickable = false
            }
        }
    }

    private fun boja(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    // ============================================================
    // KRAJ MEČA
    // ============================================================

    private fun showFinal(state: MatchState) {
        if (finalShown) return
        finalShown = true
        timer?.cancel()
        val my = state.myScore(mp.uid)
        val opp = state.opponentScore(mp.uid)
        val title = when {
            state.winnerId == null -> getString(R.string.mp_nereseno)
            state.winnerId == mp.uid -> getString(R.string.mp_pobjeda)
            else -> getString(R.string.mp_poraz)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("Vi: $my\n${state.opponentName(mp.uid)}: $opp")
            .setPositiveButton(R.string.mp_zatvori) { _, _ ->
                mp.leaveMatch()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        advanceJob?.cancel()
        _binding = null
    }

    companion object {
        private const val REVEAL_PAUSE_MS = 7_000L     // pregled otkrivene table
        private const val POKUSAJ_PRIKAZ_MS = 3_000    // prikaz tuđeg pokušaja
    }
}
