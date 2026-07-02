package com.example.slagalica.data

import com.example.slagalica.model.ChatMessage
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Čet po regionima (spec 8) - igrači istog regiona vide istu kolekciju poruka u realnom vremenu. */
class ChatRepository {

    private val db = FirebaseProvider.db

    private fun messages(region: String) =
        db.collection(FirestoreCollections.CHATS)
            .document(region.ifBlank { "opsti" })
            .collection(FirestoreCollections.CHAT_MESSAGES)

    fun listen(region: String, onChange: (List<ChatMessage>) -> Unit): ListenerRegistration {
        return messages(region)
            .orderBy("timestampMs", Query.Direction.ASCENDING)
            .limitToLast(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { d ->
                    runCatching {
                        ChatMessage(
                            id = d.id,
                            senderUid = d.getString("senderUid") ?: "",
                            senderName = d.getString("senderName") ?: "Igrač",
                            text = d.getString("text") ?: "",
                            timestampMs = d.getLong("timestampMs") ?: 0L
                        )
                    }.getOrNull()
                }
                onChange(list)
            }
    }

    suspend fun posalji(region: String, senderUid: String, senderName: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        messages(region).add(
            mapOf(
                "senderUid" to senderUid,
                "senderName" to senderName,
                "text" to trimmed,
                "timestampMs" to System.currentTimeMillis()
            )
        ).await()
    }
}
