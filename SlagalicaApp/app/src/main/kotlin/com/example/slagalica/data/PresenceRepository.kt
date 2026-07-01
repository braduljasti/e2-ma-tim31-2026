package com.example.slagalica.data

import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Prisustvo igrača: upisuje `lastSeen` timestamp ulogovanog korisnika. Koristi
 * se za "aktivni igrači" u statistici regiona (spec 5.d) i online status prijatelja.
 * Poziva se pri pokretanju/aktivaciji aplikacije (MainActivity.onResume).
 */
class PresenceRepository {

    private val db = FirebaseProvider.db

    suspend fun azuriraj() {
        val uid = FirebaseProvider.currentUid ?: return
        db.collection(FirestoreCollections.USERS).document(uid)
            .set(mapOf("lastSeen" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    companion object {
        /** Igrač se smatra aktivnim ako je viđen u zadnjih 5 minuta. */
        const val AKTIVAN_PRAG_MS = 5 * 60 * 1000L
    }
}
