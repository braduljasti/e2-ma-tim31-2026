package com.example.slagalica.ui.games

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.slagalica.R
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.databinding.FragmentKoZnaZnaBinding
import com.example.slagalica.model.KzzKonstante
import com.example.slagalica.model.KzzOdgovor
import com.example.slagalica.model.KzzPitanje
import com.example.slagalica.model.MatchState
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Multiplayer Ko zna zna: oba telefona dobijaju ISTA pitanja (ugrađena u
 * matches/{id} prilikom uparivanja). Igrač lokalno odgovara na 5 pitanja
 * (5s po pitanju); bilježi se i vrijeme odgovora zbog pravila "ako oba
 * tačno odgovore - bodove dobija brži". Na kraju se svi odgovori šalju u
 * Firestore; host (player1) boduje rundu, rezultat stiže kroz MatchState.
 */
class KzzMpFragment : Fragment() {

    private var _binding: FragmentKoZnaZnaBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel
    private lateinit var dugmadOdgovora: List<MaterialButton>

    // Lokalni tok kviza
    private var pitanja: List<KzzPitanje> = emptyList()
    private val mojiOdgovori = mutableListOf<KzzOdgovor>()
    private var trenutnoPitanje = 0
    private var pitanjeStartedAt = 0L
    private var odgovaranjeAktivno = false
    private var quizStarted = false
    private var submitted = false
    private var finalShown = false

    private var timer: CountDownTimer? = null
    private var advanceJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKoZnaZnaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]

        dugmadOdgovora = listOf(
            binding.btnOdgovorA,
            binding.btnOdgovorB,
            binding.btnOdgovorC,
            binding.btnOdgovorD
        )
        dugmadOdgovora.forEachIndexed { index, button ->
            button.setOnClickListener { onAnswerSelected(index) }
        }
        binding.btnDalje.setOnClickListener { onSkip() }

        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    // ============================================================
    // SINHRONIZACIJA SA MEČOM
    // ============================================================

    private fun onMatchUpdate(state: MatchState) {
        binding.scoreboardKzz.tvMojiBodovi.text = state.myScore(mp.uid).toString()
        binding.scoreboardKzz.tvProtivnikBodovi.text = state.opponentScore(mp.uid).toString()

        if (state.finished) { showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != MultiplayerRepository.GAME_KZZ) return

        // Pitanja stižu kroz prvi snapshot meča - kviz se pokreće samo jednom
        if (!quizStarted) {
            quizStarted = true
            pitanja = round.kzzPitanja()
            if (pitanja.isNotEmpty()) loadQuestion(0)
        }
    }

    // ============================================================
    // LOKALNI TOK KVIZA (5 pitanja x 5 sekundi)
    // ============================================================

    private fun loadQuestion(index: Int) {
        trenutnoPitanje = index
        val pitanje = pitanja[index]

        binding.tvTekstPitanja.text = pitanje.tekst
        val labele = listOf("A", "B", "C", "D")
        dugmadOdgovora.forEachIndexed { i, b ->
            b.text = "${labele[i]}) ${pitanje.odgovori[i]}"
            stilirajPocetno(b)
        }
        binding.tvBrojPitanja.text = getString(R.string.kzz_pitanje_format, index + 1, pitanja.size)
        binding.progressPitanja.progress = ((index + 1) * 100) / pitanja.size
        binding.btnDalje.isEnabled = true

        odgovaranjeAktivno = true
        pitanjeStartedAt = System.currentTimeMillis()
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(KzzKonstante.VREME_PO_PITANJU_S * 1000L, 200L) {
            override fun onTick(millisUntilFinished: Long) {
                val sekundi = ((millisUntilFinished + 999L) / 1000L).toInt()
                binding.tvTimerKzz.text = sekundi.toString()
                binding.cardTimerKzz.setCardBackgroundColor(ContextCompat.getColor(requireContext(), when {
                    sekundi >= 4 -> R.color.timer_normalno
                    sekundi >= 2 -> R.color.timer_upozorenje
                    else -> R.color.timer_hitno
                }))
            }

            override fun onFinish() {
                binding.tvTimerKzz.text = "0"
                onNoAnswer()
            }
        }.start()
    }

    private fun onAnswerSelected(index: Int) {
        if (!odgovaranjeAktivno) return
        odgovaranjeAktivno = false
        timer?.cancel()

        val vreme = System.currentTimeMillis() - pitanjeStartedAt
        mojiOdgovori.add(KzzOdgovor(index, vreme))

        // Vizuelni feedback: tačan zeleno, moj netačan crveno, ostali prigušeni
        val tacan = pitanja[trenutnoPitanje].tacanIndex
        dugmadOdgovora.forEachIndexed { i, b ->
            when {
                i == tacan -> stilirajTacan(b)
                i == index -> stilirajNetacan(b)
                else -> stilirajPrigusen(b)
            }
        }
        binding.btnDalje.isEnabled = false
        scheduleNext()
    }

    private fun onSkip() {
        if (!odgovaranjeAktivno) return
        odgovaranjeAktivno = false
        timer?.cancel()
        onNoAnswerCommon()
    }

    private fun onNoAnswer() {
        if (!odgovaranjeAktivno) return
        odgovaranjeAktivno = false
        onNoAnswerCommon()
    }

    /** Istekao tajmer ili preskočeno: bez odgovora (0 bodova), otkrij tačan. */
    private fun onNoAnswerCommon() {
        mojiOdgovori.add(KzzOdgovor(KzzOdgovor.NIJE_ODGOVORIO, 0))
        val tacan = pitanja[trenutnoPitanje].tacanIndex
        dugmadOdgovora.forEachIndexed { i, b ->
            if (i == tacan) stilirajTacan(b) else stilirajPrigusen(b)
        }
        binding.btnDalje.isEnabled = false
        scheduleNext()
    }

    private fun scheduleNext() {
        advanceJob?.cancel()
        advanceJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(FEEDBACK_DELAY_MS)
            val sledeci = trenutnoPitanje + 1
            if (sledeci >= pitanja.size) submitAnswers() else loadQuestion(sledeci)
        }
    }

    /** Šalje sve odgovore u Firestore i čeka da protivnik završi svoj kviz. */
    private fun submitAnswers() {
        if (submitted) return
        submitted = true
        timer?.cancel()
        mp.submitKzz(mojiOdgovori.toList())

        binding.tvTekstPitanja.text = getString(R.string.mp_cekamo_protivnika)
        dugmadOdgovora.forEach { stilirajPrigusen(it) }
        binding.btnDalje.isEnabled = false
    }

    // ============================================================
    // STILIZACIJA DUGMADI (kao u KzzFragment)
    // ============================================================

    private fun stilirajPocetno(b: MaterialButton) {
        b.isEnabled = true
        b.backgroundTintList = null
        b.setTextColor(boja(R.color.text_primary))
        b.strokeColor = ColorStateList.valueOf(boja(R.color.primary_light))
    }

    private fun stilirajTacan(b: MaterialButton) {
        b.isEnabled = false
        val zelena = boja(R.color.success)
        b.backgroundTintList = ColorStateList.valueOf(zelena)
        b.setTextColor(boja(R.color.white))
        b.strokeColor = ColorStateList.valueOf(zelena)
    }

    private fun stilirajNetacan(b: MaterialButton) {
        b.isEnabled = false
        val crvena = boja(R.color.error)
        b.backgroundTintList = ColorStateList.valueOf(crvena)
        b.setTextColor(boja(R.color.white))
        b.strokeColor = ColorStateList.valueOf(crvena)
    }

    private fun stilirajPrigusen(b: MaterialButton) {
        b.isEnabled = false
        b.backgroundTintList = null
        b.setTextColor(boja(R.color.text_hint))
        b.strokeColor = ColorStateList.valueOf(boja(R.color.text_hint))
    }

    private fun boja(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    // ============================================================
    // KRAJ MEČA
    // ============================================================

    private fun showFinal(state: MatchState) {
        if (finalShown) return
        finalShown = true
        timer?.cancel()
        advanceJob?.cancel()
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
        private const val FEEDBACK_DELAY_MS = 1500L
    }
}
