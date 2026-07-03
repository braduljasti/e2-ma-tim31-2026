package com.example.slagalica.data

import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class PresenceRepository {

    private val db = FirebaseProvider.db

    suspend fun azuriraj() {
        val uid = FirebaseProvider.currentUid ?: return
        db.collection(FirestoreCollections.USERS).document(uid)
            .set(mapOf("lastSeen" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    companion object {
        const val AKTIVAN_PRAG_MS = 5 * 60 * 1000L
    }
}
