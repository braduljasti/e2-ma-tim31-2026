package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.AuthRepository
import com.example.slagalica.data.FirebaseProvider
import com.example.slagalica.data.GameLogic
import com.example.slagalica.data.GameResultRepository
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.data.ProfilRepository
import com.example.slagalica.model.GameType
import com.example.slagalica.model.KzzOdgovor
import com.example.slagalica.model.MatchRewardOutcome
import com.example.slagalica.model.MatchState
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MultiplayerViewModel(
    private val repo: MultiplayerRepository = MultiplayerRepository(),
    private val authRepo: AuthRepository = AuthRepository(),
    private val resultsRepo: GameResultRepository = GameResultRepository(),
    private val profilRepo: ProfilRepository = ProfilRepository()
) : ViewModel() {

    val uid: String get() = FirebaseProvider.currentUid ?: ""

    private val _searching = MutableLiveData(false)
    val searching: LiveData<Boolean> = _searching

    private val _matchFound = MutableLiveData<String?>()
    val matchFound: LiveData<String?> = _matchFound

    private val _match = MutableLiveData<MatchState?>()
    val match: LiveData<MatchState?> = _match

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** Popunjava se kada je ishod partije (zvezde/tokeni/liga) obračunat - vidi ProgressionRepository. */
    private val _rewardOutcome = MutableLiveData<MatchRewardOutcome?>()
    val rewardOutcome: LiveData<MatchRewardOutcome?> = _rewardOutcome

    private var ticketListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var resultSaved = false
    private var rewardApplied = false
    private var lastMatchId: String? = null

    var requestedGameType: String = MultiplayerRepository.GAME_SKOCKO
        private set

    /** Pravi meč (partija od 6 igara) - troši 1 token. Koristi se za glavno dugme "Igraj!". */
    fun startPartijaMatchmaking() {
        viewModelScope.launch {
            val imaTokena = runCatching { profilRepo.potrosiToken(uid) }.getOrDefault(false)
            if (!imaTokena) {
                _error.postValue("Nemate dovoljno tokena za partiju. Sačekajte dnevnu dodjelu ili osvojite tokene na rang listi!")
                return@launch
            }
            startMatchmaking(MultiplayerRepository.GAME_PARTIJA)
        }
    }

    fun consumeError() { _error.value = null }
    fun consumeRewardOutcome() { _rewardOutcome.value = null }

    /** Napušta se trenutna partija - protivnik se odmah proglašava pobednikom, bez čekanja. */
    fun forfeitMatch() {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching { repo.leaveMatch(state.id, uid) }
        }
    }

    fun startMatchmaking(gameType: String) {
        requestedGameType = gameType
        _searching.value = true
        viewModelScope.launch {
            val myName = currentUsername()
            val joined = runCatching { repo.tryJoin(uid, myName, gameType) }.getOrNull()
            if (joined != null) {
                onMatched(joined)
            } else {
                ticketListener = repo.createTicketAndWait(
                    uid, myName, gameType,
                    onMatched = { id -> onMatched(id) },
                    onError = { _searching.postValue(false) }
                )
            }
        }
    }

    private fun onMatched(matchId: String) {
        ticketListener?.remove(); ticketListener = null
        lastMatchId = matchId
        _searching.postValue(false)
        _matchFound.postValue(matchId)
    }

    fun bindCurrentMatch() { lastMatchId?.let { bindMatch(it) } }

    fun cancelMatchmaking() {
        ticketListener?.remove(); ticketListener = null
        _searching.value = false
        viewModelScope.launch { repo.cancelMatchmaking(uid) }
    }

    fun consumeMatchFound() { _matchFound.value = null }

    fun bindMatch(matchId: String) {
        matchListener?.remove()
        resultSaved = false
        rewardApplied = false
        matchListener = repo.listenMatch(matchId) { state ->
            _match.postValue(state)

            val isHost = state.isPlayer1(uid)
            val round = state.currentRound
            if (isHost && !state.finished && round != null && round.bothSubmitted && !round.resolved) {
                viewModelScope.launch { runCatching { repo.hostResolveIfReady(matchId) } }
            }

            if (state.finished && !resultSaved) {
                resultSaved = true
                saveMyResult(state)
            }
            if (state.finished && !rewardApplied) {
                rewardApplied = true
                applyReward(state)
            }
        }
    }

    private fun applyReward(state: MatchState) {
        // Trening (pojedinačna igra) se ne boduje zvezdama/tokenima/ligom - samo prava partija.
        if (state.gameType != MultiplayerRepository.GAME_PARTIJA) return
        viewModelScope.launch {
            val outcome = runCatching { repo.primeniNagraduAkoTreba(state.id, uid) }.getOrNull()
            if (outcome != null) _rewardOutcome.postValue(outcome)
        }
    }

    fun submitSkocko(guesses: List<List<Int>>) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching { repo.submitSkocko(state.id, state.isPlayer1(uid), guesses) }
        }
    }

    fun submitKzz(odgovori: List<KzzOdgovor>) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching { repo.submitKzz(state.id, state.isPlayer1(uid), odgovori) }
        }
    }

    fun submitSpojnice(parovi: List<Pair<Int, Int>>) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching { repo.submitSpojnice(state.id, state.isPlayer1(uid), parovi) }
        }
    }

    fun submitKorak(guess: String, step: Int) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching { repo.submitKorak(state.id, state.isPlayer1(uid), guess, step) }
        }
    }

    fun submitMojBroj(expr: String) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching { repo.submitMojBroj(state.id, state.isPlayer1(uid), expr) }
        }
    }

    fun spojniceLivePotez(par: Pair<Int, Int>) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching {
                repo.spojniceLivePotez(state.id, state.isPlayer1(uid), state.currentRoundIndex, par)
            }
        }
    }

    fun asocijacijeOtvoriPolje(col: Int, row: Int) = asocPotez { id, idx ->
        repo.asocijacijeOtvoriPolje(id, idx, uid, col, row)
    }

    fun asocijacijePogodiKolonu(col: Int, guess: String) = asocPotez { id, idx ->
        repo.asocijacijePogodiKolonu(id, idx, uid, col, guess)
    }

    fun asocijacijePogodiFinalno(guess: String) = asocPotez { id, idx ->
        repo.asocijacijePogodiFinalno(id, idx, uid, guess)
    }

    fun asocijacijePropusti() = asocPotez { id, idx ->
        repo.asocijacijePropusti(id, idx, uid)
    }

    fun asocijacijeIstekloVreme() = asocPotez { id, idx ->
        repo.asocijacijeIstekloVreme(id, idx)
    }

    fun asocijacijeSledecaRunda() = asocPotez { id, idx ->
        repo.asocijacijeSledecaRunda(id, idx)
    }

    private fun asocPotez(akcija: suspend (matchId: String, roundIndex: Int) -> Unit) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching { akcija(state.id, state.currentRoundIndex) }
        }
    }

    private fun saveMyResult(state: MatchState) {
        if (state.gameType == MultiplayerRepository.GAME_PARTIJA) {
            saveMyResultsPartija(state)
            return
        }
        val type = gameTypeToEnum(state.gameType)
        val my = state.myScore(uid)
        val opp = state.opponentScore(uid)
        val details = buildDetails(state.gameType, state.rounds, state.isPlayer1(uid))
        viewModelScope.launch {
            runCatching { resultsRepo.saveResult(type, my, opp, details) }
        }
    }

    /** Partija = svih 6 igara odjednom; sačuvaj po jedan GameResult za svaku odigranu pod-igru
     * (tako i dalje rade postojeći ekrani statistike u Profilu, po igri). */
    private fun saveMyResultsPartija(state: MatchState) {
        val isP1 = state.isPlayer1(uid)
        val poGrupama = state.rounds.groupBy { it.gameType }
        viewModelScope.launch {
            poGrupama.forEach { (gameType, runde) ->
                val my = runde.sumOf { if (isP1) it.p1Points else it.p2Points }
                val opp = runde.sumOf { if (isP1) it.p2Points else it.p1Points }
                val details = buildDetails(gameType, runde, isP1)
                runCatching { resultsRepo.saveResult(gameTypeToEnum(gameType), my, opp, details) }
            }
        }
    }

    private fun gameTypeToEnum(gameType: String): GameType = when (gameType) {
        MultiplayerRepository.GAME_KZZ -> GameType.KO_ZNA_ZNA
        MultiplayerRepository.GAME_SPOJNICE -> GameType.SPOJNICE
        MultiplayerRepository.GAME_ASOCIJACIJE -> GameType.ASOCIJACIJE
        MultiplayerRepository.GAME_KORAK -> GameType.KORAK_PO_KORAK
        MultiplayerRepository.GAME_MOJ_BROJ -> GameType.MOJ_BROJ
        else -> GameType.SKOCKO
    }

    private fun buildDetails(gameType: String, rounds: List<com.example.slagalica.model.RoundState>, isP1: Boolean): Map<String, Long> {
        fun mojSub(r: com.example.slagalica.model.RoundState) = if (isP1) r.p1Sub else r.p2Sub

        return when (gameType) {

            MultiplayerRepository.GAME_KZZ -> {
                val round = rounds.firstOrNull() ?: return emptyMap()
                val tacniIndeksi = round.kzzPitanja().map { it.tacanIndex }
                val moji = round.kzzOdgovori(mojSub(round))
                var tacnih = 0L; var netacnih = 0L; var bezOdgovora = 0L
                tacniIndeksi.forEachIndexed { i, tacan ->
                    val o = moji.getOrNull(i)
                    when {
                        o == null || o.index == KzzOdgovor.NIJE_ODGOVORIO -> bezOdgovora++
                        o.index == tacan -> tacnih++
                        else -> netacnih++
                    }
                }
                mapOf("tacnih" to tacnih, "netacnih" to netacnih, "bezOdgovora" to bezOdgovora)
            }

            MultiplayerRepository.GAME_SPOJNICE -> {
                var povezanih = 0L; var pokusaja = 0L
                rounds.forEach { r ->
                    val veze = r.spojniceRunda()?.tacneVeze ?: return@forEach
                    val moji = r.spojniceParovi(mojSub(r))
                    pokusaja += moji.size
                    povezanih += moji.count { veze[it.first] == it.second }
                }
                mapOf("povezanih" to povezanih, "pokusaja" to pokusaja)
            }

            MultiplayerRepository.GAME_ASOCIJACIJE -> {
                var resenihFinala = 0L; var resenihKolona = 0L
                rounds.forEach { r ->
                    if (r.asocResenoFinalnoUid() == uid) resenihFinala++
                    resenihKolona += r.asocReseneKolone().values.count { it == uid }
                }
                mapOf(
                    "resenihFinala" to resenihFinala,
                    "resenihKolona" to resenihKolona,
                    "rundi" to rounds.size.toLong()
                )
            }

            else -> {
                var resenihRundi = 0L
                rounds.forEach { r ->
                    val secret = r.skockoSecret()
                    val moji = r.skockoGuesses(mojSub(r))
                    if (moji.any { GameLogic.evaluateSkocko(secret, it).first == 4 }) resenihRundi++
                }
                mapOf("resenihRundi" to resenihRundi, "rundi" to rounds.size.toLong())
            }
        }
    }

    private suspend fun currentUsername(): String =
        runCatching { authRepo.currentUserProfile()?.username }.getOrNull()
            ?: FirebaseProvider.auth.currentUser?.email?.substringBefore("@")
            ?: "Igrač"

    fun leaveMatch() {
        matchListener?.remove(); matchListener = null
        _match.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ticketListener?.remove()
        matchListener?.remove()
    }
}
