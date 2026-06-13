package com.example.slagalica.ui.games

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.R
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.databinding.FragmentSpojniceBinding
import com.example.slagalica.model.MatchState
import com.example.slagalica.model.SpojniceKonstante
import com.example.slagalica.model.SpojniceRundaPodaci
import com.example.slagalica.model.SpojniceStanjeCelije
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SpojniceMpFragment : Fragment() {

    private var _binding: FragmentSpojniceBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel
    private lateinit var leveKartice: List<MaterialCardView>
    private lateinit var leviTekstovi: List<TextView>
    private lateinit var desneKartice: List<MaterialCardView>
    private lateinit var desniTekstovi: List<TextView>

    private var playedRoundIndex = -1
    private var runda: SpojniceRundaPodaci? = null
    private var amStarter = false
    private var mojaFazaAktivna = false
    private var submitted = false
    private var finalShown = false

    private val mojiPokusaji = mutableListOf<Pair<Int, Int>>()
    private var aktivanLevi = -1
    private var leveStanja = MutableList(5) { SpojniceStanjeCelije.POCETNO }
    private var desnaStanja = MutableList(5) { SpojniceStanjeCelije.POCETNO }
    private var playableLefts: List<Int> = emptyList()
    private var timer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSpojniceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]

        leveKartice = listOf(
            binding.cellLevi0.root, binding.cellLevi1.root, binding.cellLevi2.root,
            binding.cellLevi3.root, binding.cellLevi4.root
        )
        leviTekstovi = listOf(
            binding.cellLevi0.tvSpojnicaCelija, binding.cellLevi1.tvSpojnicaCelija,
            binding.cellLevi2.tvSpojnicaCelija, binding.cellLevi3.tvSpojnicaCelija,
            binding.cellLevi4.tvSpojnicaCelija
        )
        desneKartice = listOf(
            binding.cellDesni0.root, binding.cellDesni1.root, binding.cellDesni2.root,
            binding.cellDesni3.root, binding.cellDesni4.root
        )
        desniTekstovi = listOf(
            binding.cellDesni0.tvSpojnicaCelija, binding.cellDesni1.tvSpojnicaCelija,
            binding.cellDesni2.tvSpojnicaCelija, binding.cellDesni3.tvSpojnicaCelija,
            binding.cellDesni4.tvSpojnicaCelija
        )

        leveKartice.forEach { it.isClickable = false }
        desneKartice.forEachIndexed { i, k -> k.setOnClickListener { onDesniClick(i) } }
        binding.btnDaljeSpojnice.setOnClickListener { if (mojaFazaAktivna) submitMoje() }

        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    private fun onMatchUpdate(state: MatchState) {

        val isP1 = state.isPlayer1(mp.uid)
        val resolvedRounds = state.rounds.filter { it.resolved }
        binding.scoreboardSpojnice.tvMojiBodovi.text =
            resolvedRounds.sumOf { if (isP1) it.p1Points else it.p2Points }.toString()
        binding.scoreboardSpojnice.tvProtivnikBodovi.text =
            resolvedRounds.sumOf { if (isP1) it.p2Points else it.p1Points }.toString()

        if (state.finished) { showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != MultiplayerRepository.GAME_SPOJNICE) return

        if (state.currentRoundIndex != playedRoundIndex) {
            playedRoundIndex = state.currentRoundIndex
            startLocalRound(state)
        }
        refreshPhase(state)
    }

    private fun startLocalRound(state: MatchState) {
        val round = state.currentRound ?: return
        runda = round.spojniceRunda() ?: return
        amStarter = round.starterId == mp.uid

        submitted = false
        mojaFazaAktivna = false
        mojiPokusaji.clear()
        aktivanLevi = -1
        leveStanja = MutableList(5) { SpojniceStanjeCelije.POCETNO }
        desnaStanja = MutableList(5) { SpojniceStanjeCelije.POCETNO }

        val r = runda!!
        leviTekstovi.forEachIndexed { i, tv -> tv.text = r.leviPojmovi[i] }
        desniTekstovi.forEachIndexed { i, tv -> tv.text = r.desniPojmovi[i] }
        binding.tvKriterijum.text = r.kriterijum
        binding.tvBrojRunde.text = getString(
            R.string.spojnice_runda_format, round.roundNumber, SpojniceKonstante.BROJ_RUNDI)
        binding.progressRunde.progress = (round.roundNumber * 100) / SpojniceKonstante.BROJ_RUNDI
        renderStanja()
    }

    private fun refreshPhase(state: MatchState) {
        if (submitted) {

            renderProtivnikUzivo(state)
            return
        }
        if (mojaFazaAktivna) return
        val round = state.currentRound ?: return
        val r = runda ?: return

        val mySub = if (state.isPlayer1(mp.uid)) round.p1Sub else round.p2Sub
        val starterSub = if (round.starterId == state.player1Id) round.p1Sub else round.p2Sub

        if (mySub != null) {

            submitted = true
            showWaiting(getString(R.string.mp_cekamo_protivnika))
            return
        }

        if (amStarter) {

            startMyPhase(playable = (0 until 5).toList())
        } else if (starterSub == null) {

            showWaiting(getString(R.string.mp_protivnik_na_potezu))
            renderProtivnikUzivo(state)
        } else {

            val starterTacne = round.spojniceParovi(starterSub)
                .filter { r.tacneVeze[it.first] == it.second }
            for ((levi, desni) in starterTacne) {
                leveStanja[levi] = SpojniceStanjeCelije.POVEZANA_PROTIVNIKOVA
                desnaStanja[desni] = SpojniceStanjeCelije.POVEZANA_PROTIVNIKOVA
            }
            renderStanja()
            val preostali = (0 until 5).filter { leveStanja[it] == SpojniceStanjeCelije.POCETNO }
            if (preostali.isEmpty()) {

                submitMoje()
            } else {
                startMyPhase(preostali)
            }
        }
    }

    private fun startMyPhase(playable: List<Int>) {
        mojaFazaAktivna = true
        playableLefts = playable
        binding.tvKriterijum.text = runda?.kriterijum
        selektujSledeciLevi()
        startTimer()
    }

    private fun selektujSledeciLevi() {
        val sledeci = playableLefts.firstOrNull { leveStanja[it] == SpojniceStanjeCelije.POCETNO }
        if (sledeci == null) {
            submitMoje()
            return
        }
        leveStanja[sledeci] = SpojniceStanjeCelije.SELEKTOVANA
        aktivanLevi = sledeci
        renderStanja()
    }

    private fun onDesniClick(index: Int) {
        if (!mojaFazaAktivna || aktivanLevi < 0) return
        if (desnaStanja[index] != SpojniceStanjeCelije.POCETNO) return
        val r = runda ?: return

        val levi = aktivanLevi
        val tacno = r.tacneVeze[levi] == index
        mojiPokusaji.add(levi to index)
        mp.spojniceLivePotez(levi to index)

        val novoStanje = if (tacno) SpojniceStanjeCelije.POVEZANA_MOJA_TACNO
        else SpojniceStanjeCelije.POVEZANA_MOJA_NETACNO
        leveStanja[levi] = novoStanje
        desnaStanja[index] = novoStanje
        aktivanLevi = -1
        selektujSledeciLevi()
    }

    private fun submitMoje() {
        if (submitted) return
        submitted = true
        mojaFazaAktivna = false
        timer?.cancel()

        for (i in leveStanja.indices) {
            if (leveStanja[i] == SpojniceStanjeCelije.SELEKTOVANA) {
                leveStanja[i] = SpojniceStanjeCelije.POCETNO
            }
        }
        aktivanLevi = -1
        renderStanja()

        mp.submitSpojnice(mojiPokusaji.toList())
        showWaiting(getString(R.string.mp_cekamo_protivnika))
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(SpojniceKonstante.VREME_PO_RUNDI_S * 1000L, 200L) {
            override fun onTick(millisUntilFinished: Long) {
                val sekundi = ((millisUntilFinished + 999L) / 1000L).toInt()
                binding.tvTimerSpojnice.text = sekundi.toString()
                binding.cardTimerSpojnice.setCardBackgroundColor(boja(when {
                    sekundi >= 11 -> R.color.timer_normalno
                    sekundi >= 6 -> R.color.timer_upozorenje
                    else -> R.color.timer_hitno
                }))
            }

            override fun onFinish() {
                binding.tvTimerSpojnice.text = "0"
                if (mojaFazaAktivna) submitMoje()
            }
        }.start()
    }

    private fun showWaiting(text: String) {
        timer?.cancel()
        binding.tvKriterijum.text = text
        binding.tvTimerSpojnice.text = "–"
        binding.cardTimerSpojnice.setCardBackgroundColor(boja(R.color.timer_normalno))
    }

    private fun renderStanja(
        leve: List<SpojniceStanjeCelije> = leveStanja,
        desne: List<SpojniceStanjeCelije> = desnaStanja
    ) {
        leve.forEachIndexed { i, s -> stilirajKarticu(leveKartice[i], leviTekstovi[i], s, isLevi = true) }
        desne.forEachIndexed { i, s -> stilirajKarticu(desneKartice[i], desniTekstovi[i], s, isLevi = false) }
    }

    private fun renderProtivnikUzivo(state: MatchState) {
        val round = state.currentRound ?: return
        val r = runda ?: return
        val oppLive = round.spojniceLiveParovi(isP1 = !state.isPlayer1(mp.uid))
        if (oppLive.isEmpty()) return

        val dozvoljena = setOf(SpojniceStanjeCelije.POCETNO, SpojniceStanjeCelije.POVEZANA_MOJA_NETACNO)
        val leve = leveStanja.toMutableList()
        val desne = desnaStanja.toMutableList()
        for ((levi, desni) in oppLive) {
            if (levi !in 0..4 || desni !in 0..4) continue
            if (leve[levi] !in dozvoljena || desne[desni] !in dozvoljena) continue
            val stanje = if (r.tacneVeze[levi] == desni) SpojniceStanjeCelije.POVEZANA_PROTIVNIKOVA
            else SpojniceStanjeCelije.POVEZANA_MOJA_NETACNO
            leve[levi] = stanje
            desne[desni] = stanje
        }
        renderStanja(leve, desne)
    }

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
                kartica.isClickable = !isLevi
            }
            SpojniceStanjeCelije.SELEKTOVANA -> {
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
