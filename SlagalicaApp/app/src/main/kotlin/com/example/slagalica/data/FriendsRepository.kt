package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class FriendsRepository {

    private val db = FirebaseProvider.db
    private val friendships get() = db.collection(FirestoreCollections.FRIENDSHIPS)

    private fun parId(a: String, b: String): String =
        if (a < b) "${a}_${b}" else "${b}_${a}"

    suspend fun dodajPrijatelja(friendUid: String) {
        val me = FirebaseProvider.currentUid ?: return
        if (friendUid == me) return
        friendships.document(parId(me, friendUid)).set(
            mapOf(
                "users" to listOf(me, friendUid),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun ukloniPrijatelja(friendUid: String) {
        val me = FirebaseProvider.currentUid ?: return
        friendships.document(parId(me, friendUid)).delete().await()
    }

    suspend fun prijateljiUidovi(): Set<String> {
        val me = FirebaseProvider.currentUid ?: return emptySet()
        return friendships.whereArrayContains("users", me).get().await()
            .documents.flatMap { doc ->
                (doc.get("users") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            }.filter { it != me }.toSet()
    }

    suspend fun listaPrijatelja(): List<FirebaseUser> {
        val uidovi = prijateljiUidovi()
        return uidovi.mapNotNull { uid ->
            runCatching {
                db.collection(FirestoreCollections.USERS).document(uid)
                    .get().await().toObject(FirebaseUser::class.java)
            }.getOrNull()
        }
    }

    suspend fun pretrazi(query: String): List<FirebaseUser> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val me = FirebaseProvider.currentUid
        return db.collection(FirestoreCollections.USERS).get().await()
            .documents.mapNotNull { it.toObject(FirebaseUser::class.java) }
            .filter { it.uid != me && it.username.contains(q, ignoreCase = true) }
    }
}
