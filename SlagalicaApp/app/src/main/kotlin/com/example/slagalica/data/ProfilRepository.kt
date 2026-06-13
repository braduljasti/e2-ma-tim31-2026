package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.example.slagalica.model.GameResult
import kotlinx.coroutines.tasks.await

class ProfilRepository {

    private val db = FirebaseProvider.db

    private fun userDoc() =
        db.collection(FirestoreCollections.USERS)
            .document(FirebaseProvider.currentUid ?: "anon")

    suspend fun ucitajKorisnika(): FirebaseUser? {
        if (FirebaseProvider.currentUid == null) return null
        return userDoc().get().await().toObject(FirebaseUser::class.java)
    }

    suspend fun sacuvajAvatar(avatarId: Int) {
        if (FirebaseProvider.currentUid == null) return
        userDoc().update("avatarId", avatarId).await()
    }

    suspend fun sviRezultati(): List<GameResult> {
        if (FirebaseProvider.currentUid == null) return emptyList()
        return userDoc().collection(FirestoreCollections.GAME_RESULTS)
            .get().await()
            .documents.mapNotNull { it.toObject(GameResult::class.java)?.copy(id = it.id) }
    }
}
