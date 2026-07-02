package com.example.slagalica.ui.games

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.data.GameLogic
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.databinding.FragmentKorakPoKorakBinding
import com.example.slagalica.model.KorakKonstante
import com.example.slagalica.model.MatchState
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class KorakPoKorakMpFragment : Fragment() {

    private var _binding: FragmentKorakPoKorakBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel

    private var playedRoundIndex = -1
    private var target = ""
    private var hints: List<String> = emptyList()
    private var currentStep = 1
    private val previousHints = mutableListOf<String>()
    private var submittedThisRound = false
    private var finalShown = false
    private var timer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKorakPoKorakBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]
        setupListeners()
        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    private fun onMatchUpdate(state: MatchState) {
        binding.scoreboardKorak.tvMojiBodovi.text = state.myScore(mp.uid).toString()
        binding.scoreboardKorak.tvProtivnikBodovi.text = state.opponentScore(mp.uid).toString()

        if (state.finished) { if (parentFragment !is com.example.slagalica.ui.main.PartijaMpFragment) showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != MultiplayerRepository.GAME_KORAK) return

        if (state.currentRoundIndex != playedRoundIndex) {
            playedRoundIndex = state.currentRoundIndex
            startLocalRound(state)
        }
    }

    private fun startLocalRound(state: MatchState) {
        val round = state.currentRound ?: return
        target = round.korakTarget()
        hints = round.korakHints()
        currentStep = 1
        submittedThisRound = false
        previousHints.clear()
        refreshPreviousHints()
        setInputsEnabled(true)
        binding.etOdgovorKorak.setText("")
        binding.tilOdgovorKorak.error = null
        Snackbar.make(binding.root,
            getString(R.string.lbl_runda, round.roundNumber, KorakKonstante.BROJ_RUNDI),
            Snackbar.LENGTH_SHORT).show()
        showHint(1)
    }

    private fun showHint(step: Int) {
        currentStep = step
        if (step > hints.size || step > KorakKonstante.MAX_KORAKA) { submitRound("", step) ; return }
        binding.tvAktuelniKorakTekst.text = hints[step - 1]
        binding.tvBrojKoraka.text = getString(R.string.lbl_korak, step)
        binding.progressKoraci.progress = step * 100 / KorakKonstante.MAX_KORAKA
        val possible = maxOf(0, KorakKonstante.BODOVA_PRVI_KORAK - (step - 1) * KorakKonstante.ODBITAK_PO_KORAKU)
        binding.tvBodoviKorak.text = getString(R.string.lbl_bodovi_korak, possible)
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(KorakKonstante.VRIJEME_PO_KORAKU_S * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1000).toInt()
                binding.tvTimerKorak.text = sec.toString()
                binding.tvTimerKorak.setTextColor(ContextCompat.getColor(requireContext(), when {
                    sec <= 3 -> R.color.timer_hitno; sec <= 6 -> R.color.timer_upozorenje; else -> R.color.white
                }))
            }
            override fun onFinish() { binding.tvTimerKorak.text = "0"; goToNextStep() }
        }.start()
    }

    private fun setupListeners() {
        binding.btnPogudiKorak.setOnClickListener {
            if (submittedThisRound) return@setOnClickListener
            val input = binding.etOdgovorKorak.text.toString().trim()
            if (input.isEmpty()) { binding.tilOdgovorKorak.error = "Unesite odgovor"; return@setOnClickListener }
            binding.tilOdgovorKorak.error = null
            if (GameLogic.korakCorrect(target, input)) {
                submitRound(input, currentStep)
            } else {
                Snackbar.make(binding.root, "Netačno! Pokušajte ponovo ili pređite na sljedeći korak.", Snackbar.LENGTH_SHORT).show()
                binding.etOdgovorKorak.setText("")
            }
        }
        binding.btnSledecKorak.setOnClickListener {
            if (submittedThisRound) return@setOnClickListener
            binding.etOdgovorKorak.setText("")
            binding.tilOdgovorKorak.error = null
            goToNextStep()
        }
    }

    private fun goToNextStep() {
        timer?.cancel()
        hints.getOrNull(currentStep - 1)?.let {
            previousHints.add(it); refreshPreviousHints()
        }
        if (currentStep >= hints.size) submitRound("", currentStep)
        else showHint(currentStep + 1)
    }

    private fun submitRound(guess: String, step: Int) {
        if (submittedThisRound) return
        submittedThisRound = true
        timer?.cancel()
        setInputsEnabled(false)
        mp.submitKorak(guess, step)
        binding.tvAktuelniKorakTekst.text = getString(R.string.mp_cekamo_protivnika)
    }

    private fun refreshPreviousHints() {
        binding.llPrethodnihKoraka.removeAllViews()
        previousHints.forEachIndexed { index, hint ->
            val tv = TextView(requireContext()).apply {
                text = "${index + 1}. $hint"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 13f
                setPadding(8, 4, 8, 4)
            }
            binding.llPrethodnihKoraka.addView(tv)
        }
        binding.tvLabelPrethodni.visibility = if (previousHints.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.btnPogudiKorak.isEnabled = enabled
        binding.btnSledecKorak.isEnabled = enabled
        binding.etOdgovorKorak.isEnabled = enabled
    }

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
        _binding = null
    }
}
