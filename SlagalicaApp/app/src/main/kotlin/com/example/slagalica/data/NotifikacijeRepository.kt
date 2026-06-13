package com.example.slagalica.data

import com.example.slagalica.model.AppNotification
import com.example.slagalica.model.NotificationCategory
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Firestore sloj za notifikacije (funkcionalni zahtjev 11).
 * Notifikacije se cuvaju po korisniku: users/{uid}/notifications/{notifId}.
 *
 * Podrzava:
 *  - 11.b: istorija svih sistemskih notifikacija (realtime listener)
 *  - 11.c: oznacavanje kao procitano
 *  - 11.d: filtriranje se radi u ViewModel-u nad ucitanom listom
 */
class NotifikacijeRepository {

    private val db = FirebaseProvider.db

    private fun collection() =
        db.collection(FirestoreCollections.USERS)
            .document(FirebaseProvider.currentUid ?: "anon")
            .collection(FirestoreCollections.NOTIFICATIONS)

    /**
     * Realtime osluskivanje notifikacija sortiranih od najnovije.
     * Vraca ListenerRegistration koji ViewModel treba da skine u onCleared().
     */
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

    /** 11.c - oznaci jednu notifikaciju kao procitanu. */
    suspend fun markAsRead(id: String) {
        collection().document(id).update("read", true).await()
    }

    /** Oznaci sve kao procitane. */
    suspend fun markAllAsRead(ids: List<String>) {
        val batch = db.batch()
        ids.forEach { id -> batch.update(collection().document(id), "read", true) }
        batch.commit().await()
    }

    /** Dodavanje nove notifikacije (npr. iz FCM-a ili sistemskih dogadjaja). */
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

    /**
     * Seed: ako korisnik nema nijednu notifikaciju, ubaci par primjera
     * da UI ne bude prazan na prvom logovanju. Vraca true ako je seedovano.
     */
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
