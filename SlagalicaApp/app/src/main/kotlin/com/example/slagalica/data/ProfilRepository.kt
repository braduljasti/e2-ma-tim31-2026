package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.example.slagalica.model.GameResult
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class ProfilRepository {

    private val db = FirebaseProvider.db

    private fun userDoc(uid: String = FirebaseProvider.currentUid ?: "anon") =
        db.collection(FirestoreCollections.USERS).document(uid)

    suspend fun ucitajKorisnika(): FirebaseUser? {
        if (FirebaseProvider.currentUid == null) return null
        return userDoc().get().await().toObject(FirebaseUser::class.java)
    }

    fun slusajKorisnika(onChange: (FirebaseUser?) -> Unit): ListenerRegistration {
        return userDoc().addSnapshotListener { snap, _ ->
            onChange(snap?.toObject(FirebaseUser::class.java))
        }
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

    sealed class TokenRezultat {
        object Uspjeh : TokenRezultat()
        object Nedovoljno : TokenRezultat()
        data class Greska(val poruka: String) : TokenRezultat()
    }

    suspend fun potrosiToken(uid: String? = FirebaseProvider.currentUid): TokenRezultat {
        if (uid.isNullOrBlank()) return TokenRezultat.Greska("Niste prijavljeni (nema aktivnog korisnika).")
        val ref = userDoc(uid)
        return try {
            val uspjeh = db.runTransaction { tx ->
                val snap = tx.get(ref)
                val tokeni = (snap.getLong("tokens") ?: 0L).toInt()
                if (tokeni <= 0) return@runTransaction false
                tx.update(ref, "tokens", tokeni - 1)
                true
            }.await()
            if (uspjeh) TokenRezultat.Uspjeh else TokenRezultat.Nedovoljno
        } catch (e: Exception) {
            TokenRezultat.Greska(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun vratiToken(uid: String? = FirebaseProvider.currentUid) {
        if (uid == null) return
        runCatching {
            db.collection(FirestoreCollections.USERS).document(uid)
                .update("tokens", com.google.firebase.firestore.FieldValue.increment(1)).await()
        }
    }
}
