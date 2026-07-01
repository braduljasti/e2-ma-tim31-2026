package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * Lista prijatelja i pretraga igrača (spec 7.a, 7.b).
 *
 * Prijateljstvo je OBOSTRANO i čuva se kao JEDAN dokument u top-level kolekciji
 * `friendships/{parId}`, gdje je `parId` sortirani par uid-ova ("A_B"), a polje
 * `users` sadrži oba uid-a. Tako čim jedan igrač doda drugog, prijateljstvo
 * važi za oba (oba ga vide preko `array-contains` upita), a poštuju se Firestore
 * pravila bez pisanja u tuđi dokument - i bez servera.
 */
class FriendsRepository {

    private val db = FirebaseProvider.db
    private val friendships get() = db.collection(FirestoreCollections.FRIENDSHIPS)

    /** Deterministički ID prijateljstva: sortirani par uid-ova, isti iz oba smjera. */
    private fun parId(a: String, b: String): String =
        if (a < b) "${a}_${b}" else "${b}_${a}"

    /** Kreira obostrano prijateljstvo između ulogovanog igrača i [friendUid]. */
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

    /** Uklanja prijateljstvo (za oba igrača, jer je jedan dokument). */
    suspend fun ukloniPrijatelja(friendUid: String) {
        val me = FirebaseProvider.currentUid ?: return
        friendships.document(parId(me, friendUid)).delete().await()
    }

    /** UID-ovi svih prijatelja ulogovanog korisnika. */
    suspend fun prijateljiUidovi(): Set<String> {
        val me = FirebaseProvider.currentUid ?: return emptySet()
        return friendships.whereArrayContains("users", me).get().await()
            .documents.flatMap { doc ->
                (doc.get("users") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            }.filter { it != me }.toSet()
    }

    /** Puni profili svih prijatelja (za listu sa avatarom, ligom, zvezdama). */
    suspend fun listaPrijatelja(): List<FirebaseUser> {
        val uidovi = prijateljiUidovi()
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
     * na velika/mala slova). Izostavlja sebe.
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
