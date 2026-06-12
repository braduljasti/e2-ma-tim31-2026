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
import com.example.slagalica.model.GameType
import com.example.slagalica.model.KzzOdgovor
import com.example.slagalica.model.MatchState
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * Vodi matchmaking i živi meč. Dijeli se na nivou Activity-ja
 * (ViewModelProvider(requireActivity())) da bi i ekran za traženje protivnika
 * i ekrani igara gledali isto stanje.
 */
class MultiplayerViewModel(
    private val repo: MultiplayerRepository = MultiplayerRepository(),
    private val authRepo: AuthRepository = AuthRepository(),
    private val resultsRepo: GameResultRepository = GameResultRepository()
) : ViewModel() {

    val uid: String get() = FirebaseProvider.currentUid ?: ""

    private val _searching = MutableLiveData(false)
    val searching: LiveData<Boolean> = _searching

    private val _matchFound = MutableLiveData<String?>()
    val matchFound: LiveData<String?> = _matchFound      // matchId kad se nađe protivnik

    private val _match = MutableLiveData<MatchState?>()
    val match: LiveData<MatchState?> = _match

    private var ticketListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var resultSaved = false
    private var lastMatchId: String? = null

    /** Igra koju je igrač izabrao pri matchmaking-u - koristi se i za navigaciju na pravi ekran. */
    var requestedGameType: String = MultiplayerRepository.GAME_SKOCKO
        private set

    // ===== MATCHMAKING =====

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

    /** Veže ekran igre na posljednje pronađeni meč. */
    fun bindCurrentMatch() { lastMatchId?.let { bindMatch(it) } }

    fun cancelMatchmaking() {
        ticketListener?.remove(); ticketListener = null
        _searching.value = false
        viewModelScope.launch { repo.cancelMatchmaking(uid) }
    }

    /** Poziva se nakon što ekran igre obradi navigaciju, da se event ne ponovi. */
    fun consumeMatchFound() { _matchFound.value = null }

    // ===== ŽIVI MEČ =====

    fun bindMatch(matchId: String) {
        matchListener?.remove()
        resultSaved = false
        matchListener = repo.listenMatch(matchId) { state ->
            _match.postValue(state)
            // HOST boduje rundu kad oba odigraju
            val isHost = state.isPlayer1(uid)
            val round = state.currentRound
            if (isHost && !state.finished && round != null && round.bothSubmitted && !round.resolved) {
                viewModelScope.launch { runCatching { repo.hostResolveIfReady(matchId) } }
            }
            // Na kraju meča svaki igrač snimi svoj rezultat (jednom)
            if (state.finished && !resultSaved) {
                resultSaved = true
                saveMyResult(state)
            }
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

    /** Objavljuje jedan potez uživo (Spojnice) da bi ga protivnik gledao u realnom vremenu. */
    fun spojniceLivePotez(par: Pair<Int, Int>) {
        val state = _match.value ?: return
        viewModelScope.launch {
            runCatching {
                repo.spojniceLivePotez(state.id, state.isPlayer1(uid), state.currentRoundIndex, par)
            }
        }
    }

    // ===== ASOCIJACIJE: potezi na zajedničkoj tabli =====

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

    /** Posle pauze za pregled rešenja - pomera meč na sledeću rundu / kraj. */
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
        val type = when (state.gameType) {
            MultiplayerRepository.GAME_KZZ -> GameType.KO_ZNA_ZNA
            MultiplayerRepository.GAME_SPOJNICE -> GameType.SPOJNICE
            MultiplayerRepository.GAME_ASOCIJACIJE -> GameType.ASOCIJACIJE
            else -> GameType.SKOCKO
        }
        val my = state.myScore(uid)
        val opp = state.opponentScore(uid)
        val details = buildDetails(state)
        viewModelScope.launch {
            runCatching { resultsRepo.saveResult(type, my, opp, details) }
        }
    }

    /**
     * Detalji za statistiku profila (spec 2.c) - računaju se iz finalnog
     * stanja meča, jer dokument meča sadrži sve poteze oba igrača.
     */
    private fun buildDetails(state: MatchState): Map<String, Long> {
        val isP1 = state.isPlayer1(uid)
        fun mojSub(r: com.example.slagalica.model.RoundState) = if (isP1) r.p1Sub else r.p2Sub

        return when (state.gameType) {
            // Odnos pogođenih i promašenih pitanja (2.c.ii)
            MultiplayerRepository.GAME_KZZ -> {
                val round = state.rounds.firstOrNull() ?: return emptyMap()
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
            // Procenat uspešno povezanih pojmova (2.c.vii)
            MultiplayerRepository.GAME_SPOJNICE -> {
                var povezanih = 0L; var pokusaja = 0L
                state.rounds.forEach { r ->
                    val veze = r.spojniceRunda()?.tacneVeze ?: return@forEach
                    val moji = r.spojniceParovi(mojSub(r))
                    pokusaja += moji.size
                    povezanih += moji.count { veze[it.first] == it.second }
                }
                mapOf("povezanih" to povezanih, "pokusaja" to pokusaja)
            }
            // Odnos rešenih i nerešenih asocijacija (2.c.v)
            MultiplayerRepository.GAME_ASOCIJACIJE -> {
                var resenihFinala = 0L; var resenihKolona = 0L
                state.rounds.forEach { r ->
                    if (r.asocResenoFinalnoUid() == uid) resenihFinala++
                    resenihKolona += r.asocReseneKolone().values.count { it == uid }
                }
                mapOf(
                    "resenihFinala" to resenihFinala,
                    "resenihKolona" to resenihKolona,
                    "rundi" to state.rounds.size.toLong()
                )
            }
            // Skočko: u koliko rundi je kombinacija pogođena (2.c.vi)
            else -> {
                var resenihRundi = 0L
                state.rounds.forEach { r ->
                    val secret = r.skockoSecret()
                    val moji = r.skockoGuesses(mojSub(r))
                    if (moji.any { GameLogic.evaluateSkocko(secret, it).first == 4 }) resenihRundi++
                }
                mapOf("resenihRundi" to resenihRundi, "rundi" to state.rounds.size.toLong())
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
