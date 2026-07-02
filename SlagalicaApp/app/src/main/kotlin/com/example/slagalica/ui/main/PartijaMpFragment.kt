package com.example.slagalica.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.databinding.FragmentPartijaMpBinding
import com.example.slagalica.model.MatchState
import com.example.slagalica.ui.games.AsocijacijeMpFragment
import com.example.slagalica.ui.games.KorakPoKorakMpFragment
import com.example.slagalica.ui.games.KzzMpFragment
import com.example.slagalica.ui.games.MojBrojMpFragment
import com.example.slagalica.ui.games.SkockoMpFragment
import com.example.slagalica.ui.games.SpojniceMpFragment
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Domaćin (host) fragment za pravu "partiju" iz specifikacije - 6 igara odigranih jedna za drugom
 * u istom meču. Prati koja je igra trenutno na redu (currentRound.gameType) i prebacuje odgovarajući
 * pod-fragment; sve dok se ne odigraju sve runde svih igara, MatchState.finished ostaje false.
 */
class PartijaMpFragment : Fragment() {

    private var _binding: FragmentPartijaMpBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel
    private var aktivnaIgra: String? = null
    private var finalShown = false
    private var dialogPrikazan = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartijaMpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]

        binding.btnNapustiPartiju.setOnClickListener { potvrdiNapustanje() }

        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    private fun onMatchUpdate(state: MatchState) {
        val ukupnoMoje = state.liveScore(mp.uid)
        val ukupnoProtivnik = state.rounds.sumOf {
            if (state.isPlayer1(mp.uid)) it.p2Points else it.p1Points
        }
        binding.tvPartijaUkupno.text = "Ukupno — Vi: $ukupnoMoje  Protivnik: $ukupnoProtivnik"

        if (state.finished) {
            showFinal(state)
            return
        }

        val gameType = state.currentRound?.gameType ?: return
        val indeksIgre = MultiplayerRepository.REDOSLED_PARTIJE.indexOf(gameType).coerceAtLeast(0)
        binding.tvPartijaIgra.text =
            "Igra ${indeksIgre + 1} / ${MultiplayerRepository.REDOSLED_PARTIJE.size}: ${MultiplayerRepository.nazivIgre(gameType)}"

        if (gameType != aktivnaIgra) {
            aktivnaIgra = gameType
            childFragmentManager.beginTransaction()
                .replace(binding.partijaGameContainer.id, fragmentZaIgru(gameType))
                .commit()
        }
    }

    private fun fragmentZaIgru(gameType: String): Fragment = when (gameType) {
        MultiplayerRepository.GAME_KZZ -> KzzMpFragment()
        MultiplayerRepository.GAME_SPOJNICE -> SpojniceMpFragment()
        MultiplayerRepository.GAME_ASOCIJACIJE -> AsocijacijeMpFragment()
        MultiplayerRepository.GAME_KORAK -> KorakPoKorakMpFragment()
        MultiplayerRepository.GAME_MOJ_BROJ -> MojBrojMpFragment()
        else -> SkockoMpFragment()
    }

    private fun potvrdiNapustanje() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Napustiti partiju?")
            .setMessage("Ako sada napustite partiju, automatski gubite i ne dobijate zvezdice. Protivnik odmah pobjeđuje.")
            .setPositiveButton("Napusti") { _, _ ->
                mp.forfeitMatch()
            }
            .setNegativeButton("Odustani", null)
            .show()
    }

    private fun showFinal(state: MatchState) {
        if (finalShown) return
        finalShown = true

        val my = state.myScore(mp.uid)
        val opp = state.opponentScore(mp.uid)
        val jaSamNapustio = state.iLeft(mp.uid)
        val protivnikNapustio = state.opponentLeft(mp.uid)

        val naslov = when {
            jaSamNapustio -> "Napustili ste partiju"
            protivnikNapustio -> "🎉 Pobjeda! Protivnik je napustio partiju"
            state.winnerId == null -> "Nerešeno!"
            state.winnerId == mp.uid -> "🎉 Pobjeda!"
            else -> "😔 Poraz"
        }

        mp.rewardOutcome.observe(viewLifecycleOwner) { outcome ->
            if (outcome == null || dialogPrikazan) return@observe
            dialogPrikazan = true
            mp.consumeRewardOutcome()

            val zvezdeLinija = if (outcome.deltaStars >= 0) "+${outcome.deltaStars} ⭐" else "${outcome.deltaStars} ⭐"
            val tokenLinija = if (outcome.tokensAwarded > 0) "\n🪙 +${outcome.tokensAwarded} token(a)" else ""
            val ligaLinija = when {
                outcome.promoted -> "\n\n🎉 Prešli ste u višu ligu!"
                outcome.relegated -> "\n\n⬇️ Pali ste u nižu ligu."
                else -> ""
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(naslov)
                .setMessage("Vi: $my\n${state.opponentName(mp.uid)}: $opp\n\n$zvezdeLinija$tokenLinija$ligaLinija")
                .setPositiveButton("Zatvori") { _, _ -> zavrsi() }
                .setCancelable(false)
                .show()
        }

        // Ako se nagrada ne izračuna u razumnom roku (npr. trening bez nagrada, ili je igrač
        // napustio pa se nagrada svjesno ne primjenjuje), ipak zatvori dijalog sam.
        binding.root.postDelayed({
            if (isAdded && !dialogPrikazan) {
                dialogPrikazan = true
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(naslov)
                    .setMessage("Vi: $my\n${state.opponentName(mp.uid)}: $opp")
                    .setPositiveButton("Zatvori") { _, _ -> zavrsi() }
                    .setCancelable(false)
                    .show()
            }
        }, 1500)
    }

    private fun zavrsi() {
        mp.leaveMatch()
        findNavController().popBackStack(com.example.slagalica.R.id.nav_igraj, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
