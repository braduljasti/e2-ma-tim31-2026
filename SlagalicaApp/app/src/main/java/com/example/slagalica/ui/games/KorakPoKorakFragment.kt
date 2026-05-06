package com.example.slagalica.ui.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.databinding.FragmentKorakPoKorakBinding
import com.example.slagalica.viewmodel.KorakPoKorakViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class KorakPoKorakFragment : Fragment() {

    private var _binding: FragmentKorakPoKorakBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: KorakPoKorakViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKorakPoKorakBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[KorakPoKorakViewModel::class.java]
        viewModel.startRound(1)
        observeChanges()
        setupListeners()
    }

    private fun observeChanges() {
        viewModel.currentStep.observe(viewLifecycleOwner) { step ->
            binding.tvBrojKoraka.text = getString(R.string.lbl_korak, step)
            binding.progressKoraci.progress = (step * 100) / 7
        }
        viewModel.remainingTime.observe(viewLifecycleOwner) { seconds ->
            binding.tvTimerKorak.text = seconds.toString()
            val color = when {
                seconds <= 3 -> ContextCompat.getColor(requireContext(), R.color.timer_hitno)
                seconds <= 6 -> ContextCompat.getColor(requireContext(), R.color.timer_upozorenje)
                else -> ContextCompat.getColor(requireContext(), R.color.white)
            }
            binding.tvTimerKorak.setTextColor(color)
        }
        viewModel.possiblePoints.observe(viewLifecycleOwner) { pts ->
            binding.tvBodoviKorak.text = getString(R.string.lbl_bodovi_korak, pts)
        }
        viewModel.currentHint.observe(viewLifecycleOwner) { hint ->
            binding.tvAktuelniKorakTekst.text = hint
        }
        viewModel.previousHints.observe(viewLifecycleOwner) { list ->
            refreshPreviousHints(list)
        }
        viewModel.round.observe(viewLifecycleOwner) { round ->
            binding.tvBrojKoraka.text = getString(R.string.lbl_runda, round, 2)
        }
        viewModel.gameFinished.observe(viewLifecycleOwner) { finished ->
            if (finished) showResult()
        }
    }

    private fun setupListeners() {
        binding.btnPogudiKorak.setOnClickListener {
            val input = binding.etOdgovorKorak.text.toString().trim()
            if (input.isEmpty()) {
                binding.tilOdgovorKorak.error = "Unesite odgovor"
                return@setOnClickListener
            }
            binding.tilOdgovorKorak.error = null
            val correct = viewModel.tryGuess(input)
            if (!correct) {
                Snackbar.make(binding.root, "Netačno! Pokušajte ponovo ili pređite na sljedeći korak.", Snackbar.LENGTH_SHORT).show()
                binding.etOdgovorKorak.setText("")
            }
        }
        binding.btnSledecKorak.setOnClickListener {
            binding.etOdgovorKorak.setText("")
            binding.tilOdgovorKorak.error = null
            viewModel.goToNextStep()
        }
    }

    private fun refreshPreviousHints(list: List<String>) {
        binding.llPrethodnihKoraka.removeAllViews()
        list.forEachIndexed { index, hint ->
            val tv = TextView(requireContext()).apply {
                text = "${index + 1}. $hint"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 13f
                setPadding(8, 4, 8, 4)
            }
            binding.llPrethodnihKoraka.addView(tv)
        }
        binding.tvLabelPrethodni.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showResult() {
        val guessed = viewModel.guessed.value == true
        val points = viewModel.points.value ?: 0
        val round = viewModel.round.value ?: 1
        val message = if (guessed) "✅ Tačno! Osvojili ste $points bodova u ovoj rundi." else "❌ Niste pogodili traženi pojam. 0 bodova."

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kraj runde $round")
            .setMessage(message)
            .setPositiveButton("Dalje") { dialog, _ ->
                dialog.dismiss()
                if (round == 1) viewModel.startRound(2) else showFinalResult()
            }
            .setCancelable(false)
            .show()
    }

    private fun showFinalResult() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Igra završena!")
            .setMessage(getString(R.string.lbl_vas_rezultat, viewModel.points.value ?: 0))
            .setPositiveButton("Zatvori") { _, _ -> requireActivity().onBackPressedDispatcher.onBackPressed() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
