package com.example.slagalica.ui.games

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.slagalica.R
import com.example.slagalica.databinding.FragmentKoZnaZnaBinding
import com.example.slagalica.model.KzzKonstante
import com.example.slagalica.model.KzzPitanje
import com.example.slagalica.model.KzzRezultat
import com.example.slagalica.model.KzzStanjePitanja
import com.example.slagalica.viewmodel.KzzViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class KzzFragment : Fragment() {

    private var _binding: FragmentKoZnaZnaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KzzViewModel by viewModels()

    private lateinit var dugmadOdgovora: List<MaterialButton>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKoZnaZnaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dugmadOdgovora = listOf(
            binding.btnOdgovorA,
            binding.btnOdgovorB,
            binding.btnOdgovorC,
            binding.btnOdgovorD
        )

        setupClickListeners()
        observeViewModel()
        viewModel.startGameIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupClickListeners() {
        dugmadOdgovora.forEachIndexed { index, button ->
            button.setOnClickListener { viewModel.onAnswerSelected(index) }
        }
        binding.btnDalje.setOnClickListener { viewModel.onSkip() }
    }

    private fun observeViewModel() {
        viewModel.trenutnoPitanje.observe(viewLifecycleOwner) { pitanje ->
            renderPitanje(pitanje)
        }
        viewModel.trenutniIndex.observe(viewLifecycleOwner) { index ->
            renderIndex(index)
        }
        viewModel.stanje.observe(viewLifecycleOwner) { stanje ->
            renderStanje(stanje)
        }
        viewModel.mojiBodovi.observe(viewLifecycleOwner) { bodovi ->
            binding.scoreboardKzz.tvMojiBodovi.text = bodovi.toString()
        }
        viewModel.protivnikBodovi.observe(viewLifecycleOwner) { bodovi ->
            binding.scoreboardKzz.tvProtivnikBodovi.text = bodovi.toString()
        }
        viewModel.preostaloVreme.observe(viewLifecycleOwner) { sekundi ->
            binding.tvTimerKzz.text = sekundi.toString()
        }
        viewModel.timerBojaRes.observe(viewLifecycleOwner) { colorRes ->
            binding.cardTimerKzz.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), colorRes)
            )
        }

        viewModel.odabranIndex.observe(viewLifecycleOwner) {
            renderStanje(viewModel.stanje.value ?: KzzStanjePitanja.AKTIVNO)
        }
        viewModel.krajIgre.observe(viewLifecycleOwner) { rezultat ->
            if (rezultat != null) showEndGameDialog(rezultat)
        }
    }

    private fun renderPitanje(pitanje: KzzPitanje) {
        binding.tvTekstPitanja.text = pitanje.tekst
        val labele = listOf("A", "B", "C", "D")
        dugmadOdgovora.forEachIndexed { index, button ->
            button.text = "${labele[index]}) ${pitanje.odgovori[index]}"
        }
    }

    private fun renderIndex(index: Int) {
        val ukupno = KzzKonstante.BROJ_PITANJA
        binding.tvBrojPitanja.text =
            getString(R.string.kzz_pitanje_format, index + 1, ukupno)
        binding.progressPitanja.progress = ((index + 1) * 100) / ukupno
    }

    private fun renderStanje(stanje: KzzStanjePitanja) {
        val pitanje = viewModel.trenutnoPitanje.value ?: return
        val odabran = viewModel.odabranIndex.value ?: -1
        val tacan = pitanje.tacanIndex

        when (stanje) {
            KzzStanjePitanja.AKTIVNO -> {
                dugmadOdgovora.forEach { stilirajPocetno(it) }
                binding.btnDalje.isEnabled = true
            }
            KzzStanjePitanja.ODGOVORENO -> {
                dugmadOdgovora.forEachIndexed { i, b ->
                    when {
                        i == tacan -> stilirajTacan(b)
                        i == odabran -> stilirajNetacan(b)
                        else -> stilirajPrigusen(b)
                    }
                }
                binding.btnDalje.isEnabled = false
            }
            KzzStanjePitanja.ISTEKLO -> {
                dugmadOdgovora.forEachIndexed { i, b ->
                    if (i == tacan) stilirajTacan(b) else stilirajPrigusen(b)
                }
                binding.btnDalje.isEnabled = false
            }
        }
    }

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

    private fun showEndGameDialog(rezultat: KzzRezultat) {
        val poruka = getString(
            R.string.kzz_finalni_rezultat,
            rezultat.mojiBodovi,
            rezultat.protivnikBodovi
        ) + "\n\n" + getString(
            R.string.kzz_finalni_detalji,
            rezultat.mojiTacni,
            rezultat.mojiNetacni,
            rezultat.mojiPromaseni
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.kzz_kraj_igre)
            .setMessage(poruka)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_pocni_ponovo) { _, _ ->
                viewModel.restart()
            }
            .setNegativeButton(R.string.btn_napusti_igru) { _, _ ->
                findNavController().popBackStack()
            }
            .show()
    }
}
