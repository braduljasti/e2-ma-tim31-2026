package com.example.slagalica.ui.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.slagalica.R
import com.example.slagalica.databinding.FragmentSpojniceBinding
import com.example.slagalica.model.SpojniceKonstante
import com.example.slagalica.model.SpojniceRezultat
import com.example.slagalica.model.SpojniceStanjeCelije
import com.example.slagalica.viewmodel.SpojniceViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SpojniceFragment : Fragment() {

    private var _binding: FragmentSpojniceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SpojniceViewModel by viewModels()

    private lateinit var leveKartice: List<MaterialCardView>
    private lateinit var leviTekstovi: List<TextView>
    private lateinit var desneKartice: List<MaterialCardView>
    private lateinit var desniTekstovi: List<TextView>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpojniceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        leveKartice = listOf(
            binding.cellLevi0.root,
            binding.cellLevi1.root,
            binding.cellLevi2.root,
            binding.cellLevi3.root,
            binding.cellLevi4.root
        )
        leviTekstovi = listOf(
            binding.cellLevi0.tvSpojnicaCelija,
            binding.cellLevi1.tvSpojnicaCelija,
            binding.cellLevi2.tvSpojnicaCelija,
            binding.cellLevi3.tvSpojnicaCelija,
            binding.cellLevi4.tvSpojnicaCelija
        )
        desneKartice = listOf(
            binding.cellDesni0.root,
            binding.cellDesni1.root,
            binding.cellDesni2.root,
            binding.cellDesni3.root,
            binding.cellDesni4.root
        )
        desniTekstovi = listOf(
            binding.cellDesni0.tvSpojnicaCelija,
            binding.cellDesni1.tvSpojnicaCelija,
            binding.cellDesni2.tvSpojnicaCelija,
            binding.cellDesni3.tvSpojnicaCelija,
            binding.cellDesni4.tvSpojnicaCelija
        )

        // Leve kartice nisu klikabilne - ViewModel ih sam upravlja
        leveKartice.forEach { it.isClickable = false }

        setupClickListeners()
        observeViewModel()
        viewModel.startGameIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupClickListeners() {
        // SAMO desne kartice imaju listener
        desneKartice.forEachIndexed { i, k -> k.setOnClickListener { viewModel.onDesniClick(i) } }
        binding.btnDaljeSpojnice.setOnClickListener { viewModel.onSkip() }
    }

    private fun observeViewModel() {
        viewModel.kriterijum.observe(viewLifecycleOwner) {
            binding.tvKriterijum.text = it
        }
        viewModel.trenutnaRunda.observe(viewLifecycleOwner) { renderRunda(it) }

        viewModel.leviTekstovi.observe(viewLifecycleOwner) { tekstovi ->
            tekstovi.forEachIndexed { i, t ->
                if (i < leviTekstovi.size) leviTekstovi[i].text = t
            }
        }
        viewModel.desniTekstovi.observe(viewLifecycleOwner) { tekstovi ->
            tekstovi.forEachIndexed { i, t ->
                if (i < desniTekstovi.size) desniTekstovi[i].text = t
            }
        }

        viewModel.leveStanja.observe(viewLifecycleOwner) { stanja ->
            stanja.forEachIndexed { i, s -> stilirajKarticu(leveKartice[i], leviTekstovi[i], s, isLevi = true) }
        }
        viewModel.desnaStanja.observe(viewLifecycleOwner) { stanja ->
            stanja.forEachIndexed { i, s -> stilirajKarticu(desneKartice[i], desniTekstovi[i], s, isLevi = false) }
        }

        viewModel.mojiBodovi.observe(viewLifecycleOwner) {
            binding.scoreboardSpojnice.tvMojiBodovi.text = it.toString()
        }
        viewModel.protivnikBodovi.observe(viewLifecycleOwner) {
            binding.scoreboardSpojnice.tvProtivnikBodovi.text = it.toString()
        }

        viewModel.preostaloVreme.observe(viewLifecycleOwner) {
            binding.tvTimerSpojnice.text = it.toString()
        }
        viewModel.timerBojaRes.observe(viewLifecycleOwner) { resId ->
            binding.cardTimerSpojnice.setCardBackgroundColor(boja(resId))
        }

        viewModel.krajIgre.observe(viewLifecycleOwner) { rezultat ->
            if (rezultat != null) showEndGameDialog(rezultat)
        }
    }

    private fun renderRunda(index: Int) {
        val ukupno = SpojniceKonstante.BROJ_RUNDI
        binding.tvBrojRunde.text = getString(R.string.spojnice_runda_format, index + 1, ukupno)
        binding.progressRunde.progress = ((index + 1) * 100) / ukupno
    }

    /**
     * Stilizovanje kartice na osnovu njenog stanja.
     * isLevi parametar: levi pojmovi nikad ne mogu biti klikabilni od strane korisnika.
     */
    private fun stilirajKarticu(
        kartica: MaterialCardView,
        tekst: TextView,
        stanje: SpojniceStanjeCelije,
        isLevi: Boolean
    ) {
        when (stanje) {
            SpojniceStanjeCelije.POCETNO -> {
                kartica.setCardBackgroundColor(boja(R.color.surface))
                kartica.strokeColor = boja(R.color.primary_light)
                kartica.strokeWidth = dp(1)
                tekst.setTextColor(boja(R.color.text_primary))
                // Samo desne POCETNO kartice su klikabilne
                kartica.isClickable = !isLevi
            }
            SpojniceStanjeCelije.SELEKTOVANA -> {
                // Samo levi pojmovi mogu biti SELEKTOVANA - to je auto-fokus
                kartica.setCardBackgroundColor(boja(R.color.accent))
                kartica.strokeColor = boja(R.color.accent_dark)
                kartica.strokeWidth = dp(3)
                tekst.setTextColor(boja(R.color.primary_dark))
                kartica.isClickable = false
            }
            SpojniceStanjeCelije.POVEZANA_MOJA_TACNO -> {
                kartica.setCardBackgroundColor(boja(R.color.success))
                kartica.strokeColor = boja(R.color.success)
                kartica.strokeWidth = dp(1)
                tekst.setTextColor(boja(R.color.white))
                kartica.isClickable = false
            }
            SpojniceStanjeCelije.POVEZANA_MOJA_NETACNO -> {
                kartica.setCardBackgroundColor(boja(R.color.error))
                kartica.strokeColor = boja(R.color.error)
                kartica.strokeWidth = dp(1)
                tekst.setTextColor(boja(R.color.white))
                kartica.isClickable = false
            }
            SpojniceStanjeCelije.POVEZANA_PROTIVNIKOVA -> {
                kartica.setCardBackgroundColor(boja(R.color.info))
                kartica.strokeColor = boja(R.color.info)
                kartica.strokeWidth = dp(1)
                tekst.setTextColor(boja(R.color.white))
                kartica.isClickable = false
            }
        }
    }

    private fun boja(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    /** Konvertuje dp u px za strokeWidth (koji prima px). */
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showEndGameDialog(rezultat: SpojniceRezultat) {
        val poruka = getString(
            R.string.spojnice_finalni_rezultat,
            rezultat.mojiBodovi,
            rezultat.protivnikBodovi
        ) + "\n\n" + getString(
            R.string.spojnice_finalni_detalji,
            rezultat.mojeVeze,
            rezultat.protivnikoVeza
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.spojnice_kraj_igre)
            .setMessage(poruka)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_pocni_ponovo) { _, _ -> viewModel.restart() }
            .setNegativeButton(R.string.btn_napusti_igru) { _, _ ->
                findNavController().popBackStack()
            }
            .show()
    }
}