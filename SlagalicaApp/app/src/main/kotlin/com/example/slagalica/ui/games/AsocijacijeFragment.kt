package com.example.slagalica.ui.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.slagalica.R
import com.example.slagalica.databinding.DialogAsocijacijeUnosBinding
import com.example.slagalica.databinding.FragmentAsocijacijeBinding
import com.example.slagalica.model.AsocijacijaCelijaStanje
import com.example.slagalica.model.AsocijacijeKonstante
import com.example.slagalica.model.AsocijacijeRezultat
import com.example.slagalica.viewmodel.AsocijacijeViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AsocijacijeFragment : Fragment() {

    private var _binding: FragmentAsocijacijeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AsocijacijeViewModel by viewModels()

    private lateinit var poljeKartice: List<List<MaterialCardView>>
    private lateinit var poljeTekstovi: List<List<TextView>>

    private lateinit var resenjeKartice: List<MaterialCardView>
    private lateinit var resenjeTekstovi: List<TextView>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAsocijacijeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBindings()
        setupClickListeners()
        observeViewModel()
        viewModel.startGameIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                poljeKartice[col][row].setOnClickListener {
                    viewModel.onPoljeClick(col, row)
                }
            }
        }
        resenjeKartice.forEachIndexed { colIdx, kartica ->
            kartica.setOnClickListener { onResenjeKoloneClick(colIdx) }
        }
        binding.resenjeFinalno.root.setOnClickListener { onFinalnoClick() }
        binding.btnDaljeAsoc.setOnClickListener { viewModel.onSkip() }
    }

    private fun onResenjeKoloneClick(columnIdx: Int) {
        if (!viewModel.canGuessColumn(columnIdx)) {
            if (viewModel.totalOpenedFields() == 0) {
                Toast.makeText(requireContext(), R.string.asoc_otvori_polje, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val colLetter = "ABCD"[columnIdx].toString()
        showGuessDialog(getString(R.string.asoc_pogadjanje_kolone, colLetter)) { unos ->
            val (tacno, score) = viewModel.onColumnGuessSubmitted(columnIdx, unos)
            if (tacno) {
                Toast.makeText(requireContext(),
                    getString(R.string.asoc_tacno_kolona, score),
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.asoc_netacno, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onFinalnoClick() {
        if (!viewModel.canGuessFinal()) {
            if (viewModel.totalOpenedFields() == 0) {
                Toast.makeText(requireContext(), R.string.asoc_otvori_polje, Toast.LENGTH_SHORT).show()
            }
            return
        }
        showGuessDialog(getString(R.string.asoc_pogadjanje_finalno)) { unos ->
            val (tacno, score) = viewModel.onFinalGuessSubmitted(unos)
            if (tacno) {
                Toast.makeText(requireContext(),
                    getString(R.string.asoc_tacno_finalno, score),
                    Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), R.string.asoc_netacno, Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun observeViewModel() {
        viewModel.trenutnaRunda.observe(viewLifecycleOwner) { renderRunda(it) }

        viewModel.tekstoviPolja.observe(viewLifecycleOwner) { renderPolja() }
        viewModel.stanjaPolja.observe(viewLifecycleOwner) { renderPolja() }

        viewModel.tekstoviResenjaKolona.observe(viewLifecycleOwner) { renderResenjaKolona() }
        viewModel.stanjaResenjaKolona.observe(viewLifecycleOwner) { renderResenjaKolona() }

        viewModel.tekstFinalno.observe(viewLifecycleOwner) { renderFinalno() }
        viewModel.stanjeFinalno.observe(viewLifecycleOwner) { renderFinalno() }

        viewModel.mojiBodovi.observe(viewLifecycleOwner) {
            binding.scoreboardAsoc.tvMojiBodovi.text = it.toString()
        }
        viewModel.protivnikBodovi.observe(viewLifecycleOwner) {
            binding.scoreboardAsoc.tvProtivnikBodovi.text = it.toString()
        }
        viewModel.preostaloVreme.observe(viewLifecycleOwner) {
            binding.tvTimerAsoc.text = it.toString()
        }
        viewModel.timerBojaRes.observe(viewLifecycleOwner) {
            binding.cardTimerAsoc.setCardBackgroundColor(boja(it))
        }
        viewModel.krajIgre.observe(viewLifecycleOwner) { rezultat ->
            if (rezultat != null) showEndGameDialog(rezultat)
        }
    }

    private fun renderRunda(index: Int) {
        val ukupno = AsocijacijeKonstante.BROJ_RUNDI
        binding.tvBrojRundeAsoc.text =
            getString(R.string.asoc_runda_format, index + 1, ukupno)
        binding.progressRundeAsoc.progress = ((index + 1) * 100) / ukupno
    }

    private fun renderPolja() {
        val tekstovi = viewModel.tekstoviPolja.value ?: return
        val stanja = viewModel.stanjaPolja.value ?: return
        if (tekstovi.size != 4 || stanja.size != 4) return

        for (col in 0..3) {
            for (row in 0..3) {
                val labela = "${"ABCD"[col]}${row + 1}"
                stilirajPolje(
                    poljeKartice[col][row], poljeTekstovi[col][row],
                    stanja[col][row], tekstovi[col][row], labela
                )
            }
        }
    }

    private fun renderResenjaKolona() {
        val tekstovi = viewModel.tekstoviResenjaKolona.value ?: return
        val stanja = viewModel.stanjaResenjaKolona.value ?: return
        if (tekstovi.size != 4 || stanja.size != 4) return

        for (col in 0..3) {
            val labela = "${"ABCD"[col]}"
            stilirajResenje(
                resenjeKartice[col], resenjeTekstovi[col],
                stanja[col], tekstovi[col], labela
            )
        }
    }

    private fun renderFinalno() {
        val tekst = viewModel.tekstFinalno.value ?: return
        val stanje = viewModel.stanjeFinalno.value ?: return
        stilirajResenje(
            binding.resenjeFinalno.root, binding.resenjeFinalno.tvAsocCelija,
            stanje, tekst, getString(R.string.asoc_finalno_zakljucano)
        )
    }

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

    private fun showEndGameDialog(rezultat: AsocijacijeRezultat) {
        val poruka = getString(
            R.string.asoc_finalni_rezultat,
            rezultat.mojiBodovi, rezultat.protivnikBodovi
        ) + "\n\n" + getString(
            R.string.asoc_finalni_detalji,
            rezultat.mojeResenja, rezultat.protivnikoveResenja
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.asoc_kraj_igre)
            .setMessage(poruka)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_pocni_ponovo) { _, _ -> viewModel.restart() }
            .setNegativeButton(R.string.btn_napusti_igru) { _, _ ->
                findNavController().popBackStack()
            }
            .show()
    }
}
