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
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.databinding.FragmentMojBrojBinding
import com.example.slagalica.model.MatchState
import com.example.slagalica.model.MojBrojKonstante
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MojBrojMpFragment : Fragment() {

    private var _binding: FragmentMojBrojBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel
    private lateinit var numberButtons: List<MaterialButton>

    private data class Token(val text: String, val btnIndex: Int?)

    private var playedRoundIndex = -1
    private var target = 0
    private var numbers: List<Int> = emptyList()
    private val tokens = mutableListOf<Token>()
    private var submittedThisRound = false
    private var finalShown = false
    private var timer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMojBrojBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]
        numberButtons = listOf(binding.btnBroj1, binding.btnBroj2, binding.btnBroj3,
            binding.btnBroj4, binding.btnBroj5, binding.btnBroj6)
        setupListeners()
        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    private fun onMatchUpdate(state: MatchState) {
        binding.scoreboardMojBroj.tvMojiBodovi.text = state.myScore(mp.uid).toString()
        binding.scoreboardMojBroj.tvProtivnikBodovi.text = state.opponentScore(mp.uid).toString()

        if (state.finished) { if (parentFragment !is com.example.slagalica.ui.main.PartijaMpFragment) showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != MultiplayerRepository.GAME_MOJ_BROJ) return

        if (state.currentRoundIndex != playedRoundIndex) {
            playedRoundIndex = state.currentRoundIndex
            startLocalRound(state)
        }
    }

    private fun startLocalRound(state: MatchState) {
        val round = state.currentRound ?: return
        target = round.mojBrojTarget()
        numbers = round.mojBrojNumbers()
        submittedThisRound = false
        tokens.clear()

        binding.tvTrazeniBreoj.text = target.toString()
        binding.tvTrazeniBreoj.visibility = View.VISIBLE
        binding.btnStopBroj.visibility = View.GONE
        binding.btnStopDostupni.visibility = View.GONE
        binding.tvRundaMojBroj.text = getString(R.string.lbl_runda, round.roundNumber, MojBrojKonstante.BROJ_RUNDI)

        numberButtons.forEachIndexed { i, b ->
            val num = numbers.getOrNull(i)
            if (num != null) { b.text = num.toString(); b.isEnabled = true; b.visibility = View.VISIBLE }
            else b.visibility = View.GONE
        }
        setControlsEnabled(true)
        renderExpression()
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(MojBrojKonstante.VRIJEME_S * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1000).toInt()
                binding.tvTimerMojBroj.text = sec.toString()
                binding.tvTimerMojBroj.setTextColor(ContextCompat.getColor(requireContext(), when {
                    sec <= 10 -> R.color.timer_hitno; sec <= 20 -> R.color.timer_upozorenje; else -> R.color.white
                }))
            }
            override fun onFinish() { binding.tvTimerMojBroj.text = "0"; submitRound() }
        }.start()
    }

    private fun setupListeners() {
        numberButtons.forEachIndexed { i, b -> b.setOnClickListener { addNumber(i) } }
        binding.btnOpPlus.setOnClickListener { addOp("+") }
        binding.btnOpMinus.setOnClickListener { addOp("-") }
        binding.btnOpPuta.setOnClickListener { addOp("*") }
        binding.btnOpDijeli.setOnClickListener { addOp("/") }
        binding.btnOpOtvZagrada.setOnClickListener { addOp("(") }
        binding.btnOpZatZagrada.setOnClickListener { addOp(")") }
        binding.btnObrisi.setOnClickListener { deleteLast() }
        binding.btnResetIzraz.setOnClickListener { resetExpression() }
        binding.btnProyeriMojBroj.setOnClickListener { confirmSubmit() }
    }

    private fun addNumber(btnIndex: Int) {
        if (submittedThisRound) return
        val num = numbers.getOrNull(btnIndex) ?: return
        tokens.add(Token(num.toString(), btnIndex))
        numberButtons[btnIndex].isEnabled = false
        renderExpression()
    }

    private fun addOp(op: String) {
        if (submittedThisRound) return
        tokens.add(Token(op, null)); renderExpression()
    }

    private fun deleteLast() {
        if (submittedThisRound || tokens.isEmpty()) return
        val last = tokens.removeAt(tokens.size - 1)
        last.btnIndex?.let { numberButtons[it].isEnabled = true }
        renderExpression()
    }

    private fun resetExpression() {
        if (submittedThisRound) return
        tokens.clear()
        numbers.indices.forEach { numberButtons[it].isEnabled = true }
        renderExpression()
        Snackbar.make(binding.root, "Izraz obrisan", Snackbar.LENGTH_SHORT).show()
    }

    private fun expressionString(): String = tokens.joinToString("") { it.text }

    private fun renderExpression() {
        binding.tvIzraz.text = expressionString()
    }

    private fun confirmSubmit() {
        if (submittedThisRound) return
        val expr = expressionString()
        if (expr.isBlank()) { Snackbar.make(binding.root, "Unesite izraz", Snackbar.LENGTH_SHORT).show(); return }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Provjera izraza")
            .setMessage("Vaš izraz: $expr\n\nPotvrđujete?")
            .setPositiveButton("Pošalji") { _, _ -> submitRound() }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun submitRound() {
        if (submittedThisRound) return
        submittedThisRound = true
        timer?.cancel()
        setControlsEnabled(false)
        mp.submitMojBroj(expressionString())
        Snackbar.make(binding.root, getString(R.string.mp_cekamo_protivnika), Snackbar.LENGTH_SHORT).show()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        numberButtons.forEach { it.isEnabled = enabled && it.isEnabled }
        listOf(binding.btnOpPlus, binding.btnOpMinus, binding.btnOpPuta, binding.btnOpDijeli,
            binding.btnOpOtvZagrada, binding.btnOpZatZagrada, binding.btnObrisi,
            binding.btnResetIzraz, binding.btnProyeriMojBroj).forEach { it.isEnabled = enabled }
        if (enabled) numbers.indices.forEach { i ->

            if (tokens.none { it.btnIndex == i }) numberButtons[i].isEnabled = true
        }
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
