package com.example.slagalica.data

import com.example.slagalica.model.GameResult
import com.example.slagalica.model.GameType
import kotlinx.coroutines.tasks.await

class GameResultRepository {

    private val db = FirebaseProvider.db

    private fun collection() =
        db.collection(FirestoreCollections.USERS)
            .document(FirebaseProvider.currentUid ?: "anon")
            .collection(FirestoreCollections.GAME_RESULTS)

    suspend fun saveResult(
        gameType: GameType,
        myPoints: Int,
        opponentPoints: Int,
        details: Map<String, Long> = emptyMap()
    ) {
        if (FirebaseProvider.currentUid == null) return
        val result = GameResult(
            gameType = gameType.name,
            myPoints = myPoints,
            opponentPoints = opponentPoints,
            won = myPoints >= opponentPoints,
            playedAt = System.currentTimeMillis(),
            details = details
        )
        collection().add(result).await()
    }

    suspend fun resultsFor(gameType: GameType): List<GameResult> {
        return collection()
            .whereEqualTo("gameType", gameType.name)
            .get().await()
            .documents.mapNotNull { it.toObject(GameResult::class.java)?.copy(id = it.id) }
    }
}
