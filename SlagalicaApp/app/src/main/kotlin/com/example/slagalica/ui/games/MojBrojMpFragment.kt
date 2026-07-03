package com.example.slagalica.ui.games

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.sqrt

/**
 * Igra "Moj broj" (spec 6). Uključuje dvostepeni "stop" mehanizam (6.b/c/d) i mogućnost
 * stopiranja pomoću shake senzora (6.l) - obje faze (traženi broj, pa dostupni brojevi)
 * mogu se otkriti i klikom na dugme i tresenjem telefona.
 */
class MojBrojMpFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentMojBrojBinding? = null
    private val binding get() = _binding!!

    private lateinit var mp: MultiplayerViewModel
    private lateinit var numberButtons: List<MaterialButton>

    private data class Token(val text: String, val btnIndex: Int?)

    /** Faze runde - prati koji "stop" korak je trenutno na redu (spec 6.b/c). */
    private enum class Faza { CEKA_STOP_BROJ, CEKA_STOP_DOSTUPNI, RESAVANJE, ZAVRSENO }

    private var faza = Faza.ZAVRSENO
    private var playedRoundIndex = -1
    private var target = 0
    private var numbers: List<Int> = emptyList()
    private val tokens = mutableListOf<Token>()
    private var submittedThisRound = false
    private var finalShown = false
    private var timer: CountDownTimer? = null
    private var autoRevealTimer: CountDownTimer? = null

    // ===== Shake senzor (spec 6.l) =====
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastShakeMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMojBrojBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mp = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]
        numberButtons = listOf(binding.btnBroj1, binding.btnBroj2, binding.btnBroj3,
            binding.btnBroj4, binding.btnBroj5, binding.btnBroj6)
        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        setupListeners()
        mp.bindCurrentMatch()
        mp.match.observe(viewLifecycleOwner) { state -> if (state != null) onMatchUpdate(state) }
    }

    private fun onMatchUpdate(state: MatchState) {
        binding.scoreboardMojBroj.tvMojiBodovi.text = state.mojiPoeniZaIgru(mp.uid, MultiplayerRepository.GAME_MOJ_BROJ).toString()
        binding.scoreboardMojBroj.tvProtivnikBodovi.text = state.protivnikoviPoeniZaIgru(mp.uid, MultiplayerRepository.GAME_MOJ_BROJ).toString()

        if (state.finished) { if (parentFragment !is com.example.slagalica.ui.main.PartijaMpFragment) showFinal(state); return }

        val round = state.currentRound ?: return
        if (round.gameType != MultiplayerRepository.GAME_MOJ_BROJ) return

        if (state.currentRoundIndex != playedRoundIndex) {
            playedRoundIndex = state.currentRoundIndex
            startLocalRound(state)
        }
    }

    /** Spec 6.b: runda počinje sakrivenim traženim brojem i dostupnim brojevima - oba se
     * otkrivaju kroz dvostepeni "stop" mehanizam, klikom ili tresenjem telefona. */
    private fun startLocalRound(state: MatchState) {
        val round = state.currentRound ?: return
        target = round.mojBrojTarget()
        numbers = round.mojBrojNumbers()
        submittedThisRound = false
        tokens.clear()
        timer?.cancel()

        binding.tvRundaMojBroj.text = getString(R.string.lbl_runda, round.roundNumber, MojBrojKonstante.BROJ_RUNDI)

        // Faza 1: traženi broj je sakriven, čeka se STOP (klik ili shake).
        faza = Faza.CEKA_STOP_BROJ
        binding.tvTrazeniBreoj.visibility = View.GONE
        binding.btnStopBroj.visibility = View.VISIBLE
        binding.btnStopBroj.isEnabled = true

        binding.btnStopDostupni.visibility = View.GONE
        numberButtons.forEach { it.text = ""; it.isEnabled = false; it.visibility = View.INVISIBLE }

        setControlsEnabled(false)
        renderExpression()
        binding.tvTimerMojBroj.text = "--"

        pokreniAutoOtkrivanje { otkriTrazeniBroj() }
    }

    /** Faza 1 -> 2: prikaže traženi broj (klik na Stop ili shake), pa čeka drugi STOP. */
    private fun otkriTrazeniBroj() {
        if (faza != Faza.CEKA_STOP_BROJ) return
        autoRevealTimer?.cancel()

        binding.tvTrazeniBreoj.text = target.toString()
        binding.tvTrazeniBreoj.visibility = View.VISIBLE
        binding.btnStopBroj.visibility = View.GONE

        faza = Faza.CEKA_STOP_DOSTUPNI
        binding.btnStopDostupni.visibility = View.VISIBLE
        binding.btnStopDostupni.isEnabled = true

        pokreniAutoOtkrivanje { otkriDostupneBrojeve() }
    }

    /** Faza 2 -> Rešavanje: prikaže 6 brojeva i pokreće glavni tajmer runde (spec 6.c). */
    private fun otkriDostupneBrojeve() {
        if (faza != Faza.CEKA_STOP_DOSTUPNI) return
        autoRevealTimer?.cancel()

        numberButtons.forEachIndexed { i, b ->
            val num = numbers.getOrNull(i)
            if (num != null) { b.text = num.toString(); b.isEnabled = true; b.visibility = View.VISIBLE }
            else b.visibility = View.GONE
        }
        binding.btnStopDostupni.visibility = View.GONE

        faza = Faza.RESAVANJE
        setControlsEnabled(true)
        startTimer()
    }

    /** Spec 6.d: ako se u roku od 5s ne klikne na Stop, sledeća faza se otkriva automatski. */
    private fun pokreniAutoOtkrivanje(naOtkrivanje: () -> Unit) {
        autoRevealTimer?.cancel()
        autoRevealTimer = object : CountDownTimer(5_000L, 1_000L) {
            override fun onTick(ms: Long) {}
            override fun onFinish() { naOtkrivanje() }
        }.start()
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

        // Klik na Stop dugmad (spec 6.b/c) - isti efekat kao tresenje telefona.
        binding.btnStopBroj.setOnClickListener { otkriTrazeniBroj() }
        binding.btnStopDostupni.setOnClickListener { otkriDostupneBrojeve() }
    }

    // ===== Shake senzor (spec 6.l) =====

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (faza != Faza.CEKA_STOP_BROJ && faza != Faza.CEKA_STOP_DOSTUPNI) return

        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        // Ubrzanje bez gravitacije (9.81 m/s²) - prag oko 2.7g je pouzdan znak potresa telefona.
        val ubrzanje = sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
        val sada = System.currentTimeMillis()

        if (ubrzanje > SHAKE_PRAG && sada - lastShakeMs > SHAKE_DEBOUNCE_MS) {
            lastShakeMs = sada
            when (faza) {
                Faza.CEKA_STOP_BROJ -> otkriTrazeniBroj()
                Faza.CEKA_STOP_DOSTUPNI -> otkriDostupneBrojeve()
                else -> {}
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun addNumber(btnIndex: Int) {
        if (submittedThisRound || faza != Faza.RESAVANJE) return
        val num = numbers.getOrNull(btnIndex) ?: return
        tokens.add(Token(num.toString(), btnIndex))
        numberButtons[btnIndex].isEnabled = false
        renderExpression()
    }

    private fun addOp(op: String) {
        if (submittedThisRound || faza != Faza.RESAVANJE) return
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
        if (submittedThisRound || faza != Faza.RESAVANJE) return
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
        faza = Faza.ZAVRSENO
        timer?.cancel()
        autoRevealTimer?.cancel()
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
        autoRevealTimer?.cancel()
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
        autoRevealTimer?.cancel()
        _binding = null
    }

    companion object {
        /** Prag ubrzanja (m/s², bez gravitacije) koji se smatra tresenjem telefona. */
        private const val SHAKE_PRAG = 12f
        private const val SHAKE_DEBOUNCE_MS = 1000L
    }
}
