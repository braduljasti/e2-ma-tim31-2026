package com.example.slagalica.data

import com.example.slagalica.model.AppNotification
import com.example.slagalica.model.NotificationCategory
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class NotifikacijeRepository {

    private val db = FirebaseProvider.db

    private fun collection() =
        db.collection(FirestoreCollections.USERS)
            .document(FirebaseProvider.currentUid ?: "anon")
            .collection(FirestoreCollections.NOTIFICATIONS)

    fun listen(onChange: (List<AppNotification>) -> Unit): ListenerRegistration {
        return collection()
            .orderBy("timestampMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val content = doc.getString("content") ?: ""
                    val categoryName = doc.getString("category") ?: NotificationCategory.OTHER.name
                    val category = runCatching { NotificationCategory.valueOf(categoryName) }
                        .getOrDefault(NotificationCategory.OTHER)
                    AppNotification(
                        id = doc.id,
                        title = title,
                        content = content,
                        category = category,
                        timestampMs = doc.getLong("timestampMs") ?: 0L,
                        read = doc.getBoolean("read") ?: false
                    )
                }
                onChange(list)
            }
    }

    suspend fun markAsRead(id: String) {
        collection().document(id).update("read", true).await()
    }

    suspend fun markAllAsRead(ids: List<String>) {
        val batch = db.batch()
        ids.forEach { id -> batch.update(collection().document(id), "read", true) }
        batch.commit().await()
    }

    suspend fun add(notification: AppNotification) {
        val data = mapOf(
            "title" to notification.title,
            "content" to notification.content,
            "category" to notification.category.name,
            "timestampMs" to notification.timestampMs,
            "read" to notification.read
        )
        collection().add(data).await()
    }

    suspend fun seedIfEmpty(): Boolean {
        val existing = collection().limit(1).get().await()
        if (!existing.isEmpty) return false
        val now = System.currentTimeMillis()
        val samples = listOf(
            AppNotification("", "Dobrodošli u Slagalicu!", "Vaš nalog je uspješno aktiviran.", NotificationCategory.OTHER, now, false),
            AppNotification("", "Napredak na rang listi!", "Nalazite se na 3. mjestu nedeljne rang liste.", NotificationCategory.RANK, now - 45 * 60_000L, false),
            AppNotification("", "🎁 Nagrada", "Dobili ste 3 tokena za prošli ciklus.", NotificationCategory.REWARDS, now - 3 * 3600_000L, true)
        )
        samples.forEach { add(it) }
        return true
    }
}
