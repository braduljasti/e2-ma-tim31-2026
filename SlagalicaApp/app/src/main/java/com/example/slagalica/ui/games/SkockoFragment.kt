package com.example.slagalica.ui.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.databinding.FragmentSkockoBinding
import com.example.slagalica.databinding.ItemSkockoRedBinding
import com.example.slagalica.model.SkockoAttempt
import com.example.slagalica.model.SkockoSymbol
import com.example.slagalica.viewmodel.SkockoViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class SkockoFragment : Fragment() {

    private var _binding: FragmentSkockoBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SkockoViewModel
    private val rowBindings = mutableListOf<ItemSkockoRedBinding>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSkockoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[SkockoViewModel::class.java]
        initRows()
        viewModel.startRound(1)
        observeChanges()
        setupListeners()
    }

    private fun initRows() {
        val inflater = LayoutInflater.from(requireContext())
        binding.llTablaPokusaja.removeAllViews()
        rowBindings.clear()
        for (i in 0 until 6) {
            val rowBinding = ItemSkockoRedBinding.inflate(inflater, binding.llTablaPokusaja, false)
            binding.llTablaPokusaja.addView(rowBinding.root)
            rowBindings.add(rowBinding)
        }
    }

    private fun observeChanges() {
        viewModel.currentSelection.observe(viewLifecycleOwner) { list ->
            val slots = listOf(binding.tvOdabir1, binding.tvOdabir2, binding.tvOdabir3, binding.tvOdabir4)
            slots.forEachIndexed { i, tv ->
                val symbol = list.getOrNull(i)
                tv.text = symbol?.emoji ?: ""
                tv.setTextColor(if (symbol != null) symbolColor(symbol) else ContextCompat.getColor(requireContext(), R.color.skocko_prazno))
            }
        }
        viewModel.attempts.observe(viewLifecycleOwner) { list ->
            list.forEachIndexed { index, attempt ->
                rowBindings.getOrNull(index)?.let { fillRow(it, attempt) }
            }
        }
        viewModel.attemptCount.observe(viewLifecycleOwner) { count ->
            binding.tvRundaSkocko.text = getString(R.string.lbl_pokusaj, count)
        }
        viewModel.remainingTime.observe(viewLifecycleOwner) { sec ->
            binding.tvTimerSkocko.text = sec.toString()
            binding.tvTimerSkocko.setTextColor(ContextCompat.getColor(requireContext(), when {
                sec <= 5 -> R.color.timer_hitno
                sec <= 10 -> R.color.timer_upozorenje
                else -> R.color.white
            }))
        }
        viewModel.round.observe(viewLifecycleOwner) { round ->
            binding.tvRundaSkocko.text = getString(R.string.lbl_runda, round, 2)
        }
        viewModel.gameFinished.observe(viewLifecycleOwner) { finished ->
            if (finished) showResult()
        }
    }

    private fun fillRow(rowBinding: ItemSkockoRedBinding, attempt: SkockoAttempt) {
        val fields = listOf(rowBinding.tvPolje1, rowBinding.tvPolje2, rowBinding.tvPolje3, rowBinding.tvPolje4)
        attempt.combination.forEachIndexed { i, symbol ->
            fields.getOrNull(i)?.apply { text = symbol.emoji; setTextColor(symbolColor(symbol)) }
        }
        val indicators = listOf(rowBinding.indikator1, rowBinding.indikator2, rowBinding.indikator3, rowBinding.indikator4)
        indicators.forEachIndexed { i, indicator ->
            indicator.setBackgroundResource(when {
                i < attempt.correctPosition -> R.drawable.bg_skocko_indikator_tacno
                i < attempt.correctPosition + attempt.wrongPosition -> R.drawable.bg_skocko_indikator_pogresno
                else -> R.drawable.bg_skocko_indikator_prazan
            })
        }
    }

    private fun setupListeners() {
        binding.tvZnakKvadrat.setOnClickListener { viewModel.addSymbol(SkockoSymbol.SQUARE) }
        binding.tvZnakKrug.setOnClickListener { viewModel.addSymbol(SkockoSymbol.CIRCLE) }
        binding.tvZnakSrce.setOnClickListener { viewModel.addSymbol(SkockoSymbol.HEART) }
        binding.tvZnakTrougao.setOnClickListener { viewModel.addSymbol(SkockoSymbol.TRIANGLE) }
        binding.tvZnakZvjezdica.setOnClickListener { viewModel.addSymbol(SkockoSymbol.STAR) }
        binding.tvZnakDijamant.setOnClickListener { viewModel.addSymbol(SkockoSymbol.DIAMOND) }

        binding.btnProyeriSkocko.setOnClickListener {
            if ((viewModel.currentSelection.value?.size ?: 0) != 4) {
                Snackbar.make(binding.root, "Odaberite 4 znaka prije provjere!", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val attempt = viewModel.checkSelection()
            if (attempt != null) {
                Snackbar.make(binding.root, "● ${attempt.correctPosition} tačno | ○ ${attempt.wrongPosition} pogrešno", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.btnBrisiOdabir.setOnClickListener { viewModel.deleteLastSymbol() }
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

    private fun showResult() {
        val guessed = viewModel.guessed.value == true
        val points = viewModel.points.value ?: 0
        val round = viewModel.round.value ?: 1
        val message = if (guessed) "🎉 Pogodili ste kombinaciju! +$points bodova" else "😔 Niste pogodili kombinaciju u ovoj rundi. 0 bodova."

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kraj runde $round")
            .setMessage(message)
            .setPositiveButton("Dalje") { dialog, _ ->
                dialog.dismiss()
                if (round == 1) { initRows(); viewModel.startRound(2) } else showFinalResult()
            }
            .setCancelable(false)
            .show()
    }

    private fun showFinalResult() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skočko završen!")
            .setMessage(getString(R.string.lbl_vas_rezultat, viewModel.points.value ?: 0))
            .setPositiveButton("Zatvori") { _, _ -> requireActivity().onBackPressedDispatcher.onBackPressed() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
