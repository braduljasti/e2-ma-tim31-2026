package com.example.slagalica.ui.games

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.databinding.FragmentSkockoBinding
import com.example.slagalica.databinding.ItemSkockoRedBinding
import com.example.slagalica.data.GameLogic
import com.example.slagalica.model.MatchState
import com.example.slagalica.model.SkockoSymbol
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class SkockoMpFragment : Fragment() {

    private var _binding: FragmentSkockoBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel
    private val rowBindings = mutableListOf<ItemSkockoRedBinding>()

    private var playedRoundIndex = -1
    private var secret: List<Int> = emptyList()
    private val selection = mutableListOf<Int>()
    private val myGuesses = mutableListOf<List<Int>>()
    private var attemptCount = 1
    private var submittedThisRound = false
    private var timer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSkockoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]
        initRows()
        setupListeners()
        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    private fun onMatchUpdate(state: MatchState) {
        binding.scoreboardSkocko.tvMojiBodovi.text = state.myScore(mp.uid).toString()
        binding.scoreboardSkocko.tvProtivnikBodovi.text = state.opponentScore(mp.uid).toString()

        if (state.finished) { showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != "Skocko") return

        if (state.currentRoundIndex != playedRoundIndex) {
            playedRoundIndex = state.currentRoundIndex
            startLocalRound(state)
        }
    }

    private fun startLocalRound(state: MatchState) {
        val round = state.currentRound ?: return
        secret = round.skockoSecret()
        selection.clear(); myGuesses.clear(); attemptCount = 1; submittedThisRound = false
        initRows()
        renderSelection()
        binding.tvRundaSkocko.text = getString(R.string.lbl_runda, round.roundNumber, 2)
        setInputsEnabled(true)
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(30_000L, 1_000L) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1000).toInt()
                binding.tvTimerSkocko.text = sec.toString()
                binding.tvTimerSkocko.setTextColor(ContextCompat.getColor(requireContext(), when {
                    sec <= 5 -> R.color.timer_hitno; sec <= 10 -> R.color.timer_upozorenje; else -> R.color.white
                }))
            }
            override fun onFinish() { binding.tvTimerSkocko.text = "0"; submitRound() }
        }.start()
    }

    private fun setupListeners() {
        binding.tvZnakKvadrat.setOnClickListener { addSymbol(0) }
        binding.tvZnakKrug.setOnClickListener { addSymbol(1) }
        binding.tvZnakSrce.setOnClickListener { addSymbol(2) }
        binding.tvZnakTrougao.setOnClickListener { addSymbol(3) }
        binding.tvZnakZvjezdica.setOnClickListener { addSymbol(4) }
        binding.tvZnakDijamant.setOnClickListener { addSymbol(5) }
        binding.btnBrisiOdabir.setOnClickListener {
            if (selection.isNotEmpty()) { selection.removeAt(selection.size - 1); renderSelection() }
        }
        binding.btnProyeriSkocko.setOnClickListener { checkSelection() }
    }

    private fun addSymbol(ordinal: Int) {
        if (submittedThisRound) return
        if (selection.size < 4) { selection.add(ordinal); renderSelection() }
    }

    private fun checkSelection() {
        if (submittedThisRound) return
        if (selection.size != 4) {
            Snackbar.make(binding.root, "Odaberite 4 znaka prije provjere!", Snackbar.LENGTH_SHORT).show(); return
        }
        val guess = selection.toList()
        myGuesses.add(guess)
        val (correct, misplaced) = GameLogic.evaluateSkocko(secret, guess)
        fillRow(rowBindings[attemptCount - 1], guess, correct, misplaced)
        selection.clear(); renderSelection()
        Snackbar.make(binding.root, "● $correct tačno | ○ $misplaced pogrešno", Snackbar.LENGTH_SHORT).show()

        if (correct == 4 || attemptCount >= 6) submitRound()
        else attemptCount++
    }

    private fun submitRound() {
        if (submittedThisRound) return
        submittedThisRound = true
        timer?.cancel()
        setInputsEnabled(false)
        mp.submitSkocko(myGuesses.toList())
        Snackbar.make(binding.root, "Poslato! Čekamo protivnika…", Snackbar.LENGTH_SHORT).show()
    }

    private fun initRows() {
        val inflater = LayoutInflater.from(requireContext())
        binding.llTablaPokusaja.removeAllViews()
        rowBindings.clear()
        repeat(6) {
            val rb = ItemSkockoRedBinding.inflate(inflater, binding.llTablaPokusaja, false)
            binding.llTablaPokusaja.addView(rb.root)
            rowBindings.add(rb)
        }
    }

    private fun renderSelection() {
        val slots = listOf(binding.tvOdabir1, binding.tvOdabir2, binding.tvOdabir3, binding.tvOdabir4)
        slots.forEachIndexed { i, tv ->
            val ord = selection.getOrNull(i)
            val symbol = ord?.let { SkockoSymbol.values()[it] }
            tv.text = symbol?.emoji ?: ""
            tv.setTextColor(
                if (symbol != null) symbolColor(symbol)
                else ContextCompat.getColor(requireContext(), R.color.skocko_prazno)
            )
        }
    }

    private fun fillRow(rb: ItemSkockoRedBinding, guess: List<Int>, correct: Int, misplaced: Int) {
        val fields = listOf(rb.tvPolje1, rb.tvPolje2, rb.tvPolje3, rb.tvPolje4)
        guess.forEachIndexed { i, ord ->
            val s = SkockoSymbol.values()[ord]
            fields.getOrNull(i)?.apply { text = s.emoji; setTextColor(symbolColor(s)) }
        }
        val indicators = listOf(rb.indikator1, rb.indikator2, rb.indikator3, rb.indikator4)
        indicators.forEachIndexed { i, ind ->
            ind.setBackgroundResource(when {
                i < correct -> R.drawable.bg_skocko_indikator_tacno
                i < correct + misplaced -> R.drawable.bg_skocko_indikator_pogresno
                else -> R.drawable.bg_skocko_indikator_prazan
            })
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.btnProyeriSkocko.isEnabled = enabled
        binding.btnBrisiOdabir.isEnabled = enabled
    }

    private fun symbolColor(symbol: SkockoSymbol): Int {
        val colorRes = when (symbol) {
            SkockoSymbol.SQUARE -> R.color.skocko_kvadrat
            SkockoSymbol.CIRCLE -> R.color.skocko_krug
            SkockoSymbol.HEART -> R.color.skocko_srce
            SkockoSymbol.TRIANGLE -> R.color.skocko_trougao
            SkockoSymbol.STAR -> R.color.skocko_zvezda
            SkockoSymbol.DIAMOND -> R.color.accent_dark
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private var finalShown = false
    private fun showFinal(state: MatchState) {
        if (finalShown) return
        finalShown = true
        timer?.cancel()
        val my = state.myScore(mp.uid)
        val opp = state.opponentScore(mp.uid)
        val title = when {
            state.winnerId == null -> "Nerešeno!"
            state.winnerId == mp.uid -> "🎉 Pobjeda!"
            else -> "😔 Poraz"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("Vi: $my\n${state.opponentName(mp.uid)}: $opp")
            .setPositiveButton("Zatvori") { _, _ ->
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
