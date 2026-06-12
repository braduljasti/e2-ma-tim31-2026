package com.example.slagalica.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.slagalica.R
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.databinding.FragmentIgrajBinding
import com.example.slagalica.viewmodel.IgrajViewModel
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class IgrajFragment : Fragment() {

    private var _binding: FragmentIgrajBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: IgrajViewModel
    private lateinit var mpViewModel: MultiplayerViewModel   // dijeljen na nivou Activity-ja

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIgrajBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[IgrajViewModel::class.java]
        mpViewModel = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]
        observeChanges()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnIgraj.setOnClickListener { showGamePicker() }

        binding.btnOtkaziCekanje.setOnClickListener {
            binding.cardCekanje.visibility = View.GONE
            binding.cardIgraj.visibility = View.VISIBLE
            mpViewModel.cancelMatchmaking()
        }
    }

    /**
     * Privremeni izbor igre - za KT2 svaka igra mora raditi pojedinačno.
     * Kasnije će partija automatski ređati svih 6 igara po specifikaciji.
     */
    private fun showGamePicker() {
        val nazivi = arrayOf("Ko zna zna", "Skočko")
        val tipovi = arrayOf(MultiplayerRepository.GAME_KZZ, MultiplayerRepository.GAME_SKOCKO)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mp_izaberi_igru)
            .setItems(nazivi) { _, which ->
                binding.cardIgraj.visibility = View.GONE
                binding.cardCekanje.visibility = View.VISIBLE
                mpViewModel.startMatchmaking(tipovi[which])   // matchmaking preko Firestore-a
            }
            .show()
    }

    private fun observeChanges() {
        viewModel.tokens.observe(viewLifecycleOwner) { binding.tvTokeniMain.text = it.toString() }
        viewModel.stars.observe(viewLifecycleOwner) { binding.tvZvjezdiceMain.text = it.toString() }
        viewModel.league.observe(viewLifecycleOwner) { binding.tvLigaMain.text = it }

        // Kad se nađe protivnik -> idemo na ekran igre koja je tražena
        mpViewModel.matchFound.observe(viewLifecycleOwner) { matchId ->
            if (matchId != null) {
                mpViewModel.consumeMatchFound()
                binding.cardCekanje.visibility = View.GONE
                binding.cardIgraj.visibility = View.VISIBLE
                val odrediste = when (mpViewModel.requestedGameType) {
                    MultiplayerRepository.GAME_KZZ -> R.id.nav_kzz_mp
                    else -> R.id.nav_skocko_mp
                }
                findNavController().navigate(odrediste)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
