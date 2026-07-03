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

/**
 * Igra "Korak po korak" (spec 5). Sekvencijalna - prvo igra igrač koji je počeo rundu (do 7
 * koraka, po 10s); tek ako on NE pogodi, protivnik dobija JEDNU priliku od 10 sekundi (5.e),
 * uz uvid u sve korake koje je starter već otvorio.
 */
class KorakPoKorakMpFragment : Fragment() {

    private var _binding: FragmentKorakPoKorakBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel

    private enum class Faza { CEKANJE, MOJA_IGRA, KRADJA, ZAVRSENO }

    private var playedRoundIndex = -1
    private var target = ""
    private var hints: List<String> = emptyList()
    private var currentStep = 1
    private val previousHints = mutableListOf<String>()
    private var amStarter = false
    private var faza = Faza.ZAVRSENO
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
        binding.scoreboardKorak.tvMojiBodovi.text = state.mojiPoeniZaIgru(mp.uid, MultiplayerRepository.GAME_KORAK).toString()
        binding.scoreboardKorak.tvProtivnikBodovi.text = state.protivnikoviPoeniZaIgru(mp.uid, MultiplayerRepository.GAME_KORAK).toString()

        if (state.finished) { if (parentFragment !is com.example.slagalica.ui.main.PartijaMpFragment) showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != MultiplayerRepository.GAME_KORAK) return

        if (state.currentRoundIndex != playedRoundIndex) {
            playedRoundIndex = state.currentRoundIndex
            startLocalRound(state)
        }
        refreshPhase(state)
    }

    private fun startLocalRound(state: MatchState) {
        val round = state.currentRound ?: return
        target = round.korakTarget()
        hints = round.korakHints()
        currentStep = 1
        submittedThisRound = false
        previousHints.clear()
        refreshPreviousHints()
        binding.etOdgovorKorak.setText("")
        binding.tilOdgovorKorak.error = null

        amStarter = round.starterId == mp.uid
        if (amStarter) {
            faza = Faza.MOJA_IGRA
            setInputsEnabled(true)
            Snackbar.make(binding.root,
                getString(R.string.lbl_runda, round.roundNumber, KorakKonstante.BROJ_RUNDI),
                Snackbar.LENGTH_SHORT).show()
            showHint(1)
        } else {
            faza = Faza.CEKANJE
            setInputsEnabled(false)
            timer?.cancel()
            binding.tvAktuelniKorakTekst.text = "Protivnik igra svoju rundu…"
            binding.tvTimerKorak.text = "–"
            binding.tvBrojKoraka.text = ""
            binding.tvBodoviKorak.text = ""
        }
    }

    /** Prati kad starter završi svoju rundu (spec 5.e) da bi meni krenula "krađa". */
    private fun refreshPhase(state: MatchState) {
        if (submittedThisRound || faza == Faza.MOJA_IGRA) return
        val round = state.currentRound ?: return

        if (faza == Faza.CEKANJE) {
            // Uživo pratim koliko je koraka starter otkrio - vidim ih, ali ne mogu da odgovaram
            // dok mi ne dođe red (spec: "da može, ali da čeka svoj red").
            val starterIsP1 = round.starterId == state.player1Id
            val liveKorak = (if (starterIsP1) round.p1Live else round.p2Live)
                .lastOrNull()?.toIntOrNull() ?: 0
            if (liveKorak > previousHints.size) {
                previousHints.clear()
                for (i in 0 until liveKorak.coerceIn(0, hints.size)) previousHints.add(hints[i])
                refreshPreviousHints()
                binding.tvAktuelniKorakTekst.text = "Protivnik igra svoju rundu… (pratite uživo, uskoro ste na redu)"
            }
        }

        val starterSub = if (round.starterId == state.player1Id) round.p1Sub else round.p2Sub
        if (starterSub == null) {
            // Spec 3.f: starter je napustio partiju - ne čekamo njegovih koraka, odmah krađa
            // sa svim koracima otkrivenim (nije bilo napretka od njega).
            if (faza == Faza.CEKANJE && state.opponentLeft(mp.uid) && round.starterId == state.opponentId(mp.uid)) {
                pokreniKradju(hints.size)
            }
            return
        }

        if (faza == Faza.CEKANJE) {
            val guess = starterSub["guess"] as? String ?: ""
            val otkrivenoKoraka = (starterSub["step"] as? Number)?.toInt() ?: hints.size
            if (GameLogic.korakCorrect(target, guess)) {
                predajPrazno()
            } else {
                pokreniKradju(otkrivenoKoraka)
            }
        }
    }

    /** Spec 5.e: protivnik dobija JEDNU priliku od 10 sekundi, uz sve korake koje je starter već otvorio. */
    private fun pokreniKradju(otkrivenoKoraka: Int) {
        if (faza == Faza.KRADJA || submittedThisRound) return
        faza = Faza.KRADJA
        previousHints.clear()
        for (i in 0 until otkrivenoKoraka.coerceIn(0, hints.size)) previousHints.add(hints[i])
        refreshPreviousHints()

        currentStep = otkrivenoKoraka.coerceIn(1, hints.size)
        binding.tvAktuelniKorakTekst.text = "🔥 Protivnik nije pogodio! Imate JEDNU priliku za 10 sekundi!"
        binding.tvBrojKoraka.text = ""
        binding.tvBodoviKorak.text = getString(R.string.lbl_bodovi_korak, KorakKonstante.KRADJA)
        binding.etOdgovorKorak.setText("")
        binding.tilOdgovorKorak.error = null
        setInputsEnabled(true)
        startTimer(10_000L)
    }

    private fun predajPrazno() {
        if (submittedThisRound) return
        submittedThisRound = true
        faza = Faza.ZAVRSENO
        mp.submitKorak("", KorakKonstante.MAX_KORAKA)
        binding.tvAktuelniKorakTekst.text = "Protivnik je već pogodio."
    }

    private fun showHint(step: Int) {
        currentStep = step
        if (step > hints.size || step > KorakKonstante.MAX_KORAKA) { submitRound("", step) ; return }
        binding.tvAktuelniKorakTekst.text = hints[step - 1]
        binding.tvBrojKoraka.text = getString(R.string.lbl_korak, step)
        binding.progressKoraci.progress = step * 100 / KorakKonstante.MAX_KORAKA
        val possible = maxOf(0, KorakKonstante.BODOVA_PRVI_KORAK - (step - 1) * KorakKonstante.ODBITAK_PO_KORAKU)
        binding.tvBodoviKorak.text = getString(R.string.lbl_bodovi_korak, possible)
        // Emitujemo BROJ ZAVRŠENIH koraka (step-1), ne trenutni - dok je korak "step" još u toku
        // (tajmer mu teče), on se za starterov sopstveni previousHints ne računa kao "prošao"
        // dok se ne pređe na sljedeći. Bez ovoga bi protivnik koji čeka vidio jedan korak više.
        if (amStarter) mp.korakLiveKorak(step - 1)
        startTimer(KorakKonstante.VRIJEME_PO_KORAKU_S * 1000L)
    }

    private fun startTimer(trajanjeMs: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(trajanjeMs, 1000L) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1000).toInt()
                binding.tvTimerKorak.text = sec.toString()
                binding.tvTimerKorak.setTextColor(ContextCompat.getColor(requireContext(), when {
                    sec <= 3 -> R.color.timer_hitno; sec <= 6 -> R.color.timer_upozorenje; else -> R.color.white
                }))
            }
            override fun onFinish() {
                binding.tvTimerKorak.text = "0"
                if (faza == Faza.KRADJA) submitRound(binding.etOdgovorKorak.text.toString().trim(), currentStep)
                else goToNextStep()
            }
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
            } else if (faza == Faza.KRADJA) {
                // U krađi imamo samo JEDAN pokušaj (spec 5.e) - netačan odgovor odmah završava.
                submitRound(input, currentStep)
            } else {
                Snackbar.make(binding.root, "Netačno! Pokušajte ponovo ili pređite na sljedeći korak.", Snackbar.LENGTH_SHORT).show()
                binding.etOdgovorKorak.setText("")
            }
        }
        binding.btnSledecKorak.setOnClickListener {
            if (submittedThisRound || faza != Faza.MOJA_IGRA) return@setOnClickListener
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
        faza = Faza.ZAVRSENO
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
        binding.btnSledecKorak.isEnabled = enabled && faza == Faza.MOJA_IGRA
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
