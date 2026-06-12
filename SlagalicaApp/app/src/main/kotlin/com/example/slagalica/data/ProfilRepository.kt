package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.example.slagalica.model.GameResult
import kotlinx.coroutines.tasks.await

/**
 * Podaci za ekran profila (spec 2): osnovni podaci korisnika iz users/{uid}
 * i svi odigrani rezultati iz users/{uid}/gameResults (za statistiku).
 */
class ProfilRepository {

    private val db = FirebaseProvider.db

    private fun userDoc() =
        db.collection(FirestoreCollections.USERS)
            .document(FirebaseProvider.currentUid ?: "anon")

    /** Profil ulogovanog korisnika ili null (nije ulogovan / dokument ne postoji). */
    suspend fun ucitajKorisnika(): FirebaseUser? {
        if (FirebaseProvider.currentUid == null) return null
        return userDoc().get().await().toObject(FirebaseUser::class.java)
    }

    /** Trajno čuva izbor avatara (spec 2.b). */
    suspend fun sacuvajAvatar(avatarId: Int) {
        if (FirebaseProvider.currentUid == null) return
        userDoc().update("avatarId", avatarId).await()
    }

    /** Svi odigrani rezultati - statistika se računa na klijentu. */
    suspend fun sviRezultati(): List<GameResult> {
        if (FirebaseProvider.currentUid == null) return emptyList()
        return userDoc().collection(FirestoreCollections.GAME_RESULTS)
            .get().await()
            .documents.mapNotNull { it.toObject(GameResult::class.java)?.copy(id = it.id) }
    }
}
