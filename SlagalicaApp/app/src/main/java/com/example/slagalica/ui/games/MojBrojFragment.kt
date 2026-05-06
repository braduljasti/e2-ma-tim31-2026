package com.example.slagalica.ui.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.databinding.FragmentMojBrojBinding
import com.example.slagalica.viewmodel.MojBrojViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MojBrojFragment : Fragment() {

    private var _binding: FragmentMojBrojBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MojBrojViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMojBrojBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[MojBrojViewModel::class.java]
        viewModel.startRound(1)
        observeChanges()
        setupListeners()
    }

    private fun observeChanges() {
        viewModel.points.observe(viewLifecycleOwner) { pts ->
            binding.scoreboardMojBroj.tvMojiBodovi.text = pts.toString()
        }
        viewModel.opponentPoints.observe(viewLifecycleOwner) { pts ->
            binding.scoreboardMojBroj.tvProtivnikBodovi.text = pts.toString()
        }
        viewModel.rotatingNumber.observe(viewLifecycleOwner) { num ->
            if (viewModel.numberShown.value != true) binding.tvTrazeniBreoj.text = num.toString()
        }
        viewModel.targetNumber.observe(viewLifecycleOwner) { number ->
            if (number != null) {
                binding.tvTrazeniBreoj.text = number.toString()
                binding.tvTrazeniBreoj.visibility = View.VISIBLE
                binding.btnStopBroj.visibility = View.GONE
            }
        }
        viewModel.availableNumbers.observe(viewLifecycleOwner) { list ->
            if (list.isNotEmpty()) {
                val buttons = listOf(binding.btnBroj1, binding.btnBroj2, binding.btnBroj3,
                    binding.btnBroj4, binding.btnBroj5, binding.btnBroj6)
                list.forEachIndexed { i, num ->
                    buttons.getOrNull(i)?.apply {
                        text = num.toString()
                        isEnabled = true
                        setOnClickListener { viewModel.addNumber(num) }
                    }
                }
                binding.btnStopDostupni.visibility = View.GONE
            }
        }
        viewModel.expression.observe(viewLifecycleOwner) { expr ->
            binding.tvIzraz.text = expr
        }
        viewModel.remainingTime.observe(viewLifecycleOwner) { sec ->
            binding.tvTimerMojBroj.text = sec.toString()
            binding.tvTimerMojBroj.setTextColor(ContextCompat.getColor(requireContext(), when {
                sec <= 10 -> R.color.timer_hitno
                sec <= 20 -> R.color.timer_upozorenje
                else -> R.color.white
            }))
        }
        viewModel.round.observe(viewLifecycleOwner) { round ->
            binding.tvRundaMojBroj.text = getString(R.string.lbl_runda, round, 2)
        }
        viewModel.checkResult.observe(viewLifecycleOwner) { msg ->
            if (msg != null) Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        }
        viewModel.gameFinished.observe(viewLifecycleOwner) { finished ->
            if (finished) showRoundResult(viewModel.round.value ?: 1)
        }
    }

    private fun setupListeners() {
        binding.btnStopBroj.setOnClickListener { viewModel.stopNumber() }
        binding.btnStopDostupni.setOnClickListener { viewModel.stopAvailable() }
        binding.btnOpPlus.setOnClickListener { viewModel.addOperator("+") }
        binding.btnOpMinus.setOnClickListener { viewModel.addOperator("-") }
        binding.btnOpPuta.setOnClickListener { viewModel.addOperator("*") }
        binding.btnOpDijeli.setOnClickListener { viewModel.addOperator("/") }
        binding.btnOpOtvZagrada.setOnClickListener { viewModel.addOperator("(") }
        binding.btnOpZatZagrada.setOnClickListener { viewModel.addOperator(")") }
        binding.btnObrisi.setOnClickListener { viewModel.deleteLastChar() }
        binding.btnResetIzraz.setOnClickListener {
            viewModel.resetExpression()
            Snackbar.make(binding.root, "Izraz obrisan", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnProyeriMojBroj.setOnClickListener {
            val expr = viewModel.expression.value ?: ""
            if (expr.isBlank()) {
                Snackbar.make(binding.root, "Unesite izraz", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Provjera izraza")
                .setMessage("Vaš izraz: $expr\n\nPotvrđujete provjeru?")
                .setPositiveButton("Provjeri") { _, _ -> viewModel.checkExpression() }
                .setNegativeButton("Otkaži", null)
                .show()
        }
    }

    private fun showRoundResult(round: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kraj runde $round")
            .setMessage("Ostvarili ste ${viewModel.points.value ?: 0} bodova u rundi $round.")
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
