package com.example.slagalica.data

import com.example.slagalica.model.GameResult
import com.example.slagalica.model.GameType
import kotlinx.coroutines.tasks.await

/**
 * Cuvanje rezultata odigranih igara u Firestore (Skočko, Korak po korak, Moj broj).
 * Putanja: users/{uid}/gameResults/{id}.
 *
 * Pozvati na kraju runde/meca iz odgovarajuceg ViewModel-a.
 */
class GameResultRepository {

    private val db = FirebaseProvider.db

    private fun collection() =
        db.collection(FirestoreCollections.USERS)
            .document(FirebaseProvider.currentUid ?: "anon")
            .collection(FirestoreCollections.GAME_RESULTS)

    /**
     * Snima jedan odigrani meč. Ne baca izuzetak ako korisnik nije ulogovan
     * (neregistrovani igrac samo igra - rezultat se prosto ne perzistira).
     */
    suspend fun saveResult(
        gameType: GameType,
        myPoints: Int,
        opponentPoints: Int
    ) {
        if (FirebaseProvider.currentUid == null) return  // neregistrovan igrac
        val result = GameResult(
            gameType = gameType.name,
            myPoints = myPoints,
            opponentPoints = opponentPoints,
            won = myPoints >= opponentPoints,
            playedAt = System.currentTimeMillis()
        )
        collection().add(result).await()
    }

    /** Sve odigrane partije date igre (za statistiku profila). */
    suspend fun resultsFor(gameType: GameType): List<GameResult> {
        return collection()
            .whereEqualTo("gameType", gameType.name)
            .get().await()
            .documents.mapNotNull { it.toObject(GameResult::class.java)?.copy(id = it.id) }
    }
}
