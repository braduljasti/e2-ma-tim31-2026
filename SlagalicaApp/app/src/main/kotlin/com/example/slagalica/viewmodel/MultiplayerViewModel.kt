package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.AuthRepository
import com.example.slagalica.data.FirebaseProvider
import com.example.slagalica.data.GameResultRepository
import com.example.slagalica.data.MultiplayerRepository
import com.example.slagalica.model.GameType
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

    // ===== MATCHMAKING =====

    fun startMatchmaking() {
        _searching.value = true
        viewModelScope.launch {
            val myName = currentUsername()
            val joined = runCatching { repo.tryJoin(uid, myName) }.getOrNull()
            if (joined != null) {
                onMatched(joined)
            } else {
                ticketListener = repo.createTicketAndWait(
                    uid, myName,
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

    private fun saveMyResult(state: MatchState) {
        val my = state.myScore(uid)
        val opp = state.opponentScore(uid)
        viewModelScope.launch {
            runCatching { resultsRepo.saveResult(GameType.SKOCKO, my, opp) }
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
