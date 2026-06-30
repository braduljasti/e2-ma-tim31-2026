package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Lista prijatelja i pretraga igrača (spec 7.a, 7.b).
 *
 * Prijateljstvo je jednosmjerno i čuva se kao podkolekcija
 * `users/{uid}/friends/{friendUid} → { since }`. Pošto svaki korisnik piše
 * samo svoju podkolekciju, ovo radi sa postojećim Firestore pravilima bez servera.
 */
class FriendsRepository {

    private val db = FirebaseProvider.db

    private fun friendsCol(uid: String) =
        db.collection(FirestoreCollections.USERS).document(uid)
            .collection(FirestoreCollections.FRIENDS)

    /** Dodaje igrača u listu prijatelja ulogovanog korisnika. */
    suspend fun dodajPrijatelja(friendUid: String) {
        val me = FirebaseProvider.currentUid ?: return
        if (friendUid == me) return
        friendsCol(me).document(friendUid)
            .set(mapOf("since" to System.currentTimeMillis())).await()
    }

    /** Uklanja igrača iz liste prijatelja. */
    suspend fun ukloniPrijatelja(friendUid: String) {
        val me = FirebaseProvider.currentUid ?: return
        friendsCol(me).document(friendUid).delete().await()
    }

    /** UID-ovi svih prijatelja ulogovanog korisnika. */
    suspend fun prijateljiUidovi(): Set<String> {
        val me = FirebaseProvider.currentUid ?: return emptySet()
        return friendsCol(me).get().await().documents.map { it.id }.toSet()
    }

    /** Puni profili svih prijatelja (za listu sa avatarom, ligom, zvezdama). */
    suspend fun listaPrijatelja(): List<FirebaseUser> {
        val uidovi = prijateljiUidovi()
        // Firestore nema "IN" za >10 elemenata, pa profile dohvatamo pojedinačno.
        return uidovi.mapNotNull { uid ->
            runCatching {
                db.collection(FirestoreCollections.USERS).document(uid)
                    .get().await().toObject(FirebaseUser::class.java)
            }.getOrNull()
        }
    }

    /**
     * Pretraga igrača po korisničkom imenu (spec 7.b). Za projekat sa malim
     * brojem korisnika učitavamo sve i filtriramo lokalno (substring, bez obzira
     * na velika/mala slova) - jednostavno i pouzdano. Izostavlja sebe.
     */
    suspend fun pretrazi(query: String): List<FirebaseUser> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val me = FirebaseProvider.currentUid
        return db.collection(FirestoreCollections.USERS).get().await()
            .documents.mapNotNull { it.toObject(FirebaseUser::class.java) }
            .filter { it.uid != me && it.username.contains(q, ignoreCase = true) }
    }
}
