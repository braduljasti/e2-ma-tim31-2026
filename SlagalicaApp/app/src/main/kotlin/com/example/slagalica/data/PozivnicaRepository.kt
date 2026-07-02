package com.example.slagalica.data

import com.example.slagalica.model.PozivNaPartiju
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Pozivi prijatelja na prijateljsku partiju (spec 7.c-e), bez servera.
 *
 * Tok: pošiljalac upiše dokument u `matchInvites` i sluša ga; primalac preko
 * snapshot listenera (dok mu je app otvoren) dobije poziv i u roku od 10s
 * prihvati ili odbije (spec 7.d - poslije 10s se automatski odbija). Ako
 * prihvati, ON kreira prijateljsku partiju i upiše matchId u poziv - pošiljalac
 * to vidi kroz svoj listener i obojica ulaze u meč.
 *
 * Ograničenje bez servera: sistemska push notifikacija kad primaocu app NIJE
 * pokrenut zahtijeva FCM slanje sa servera (kolegin dio) - do tada poziv radi
 * u realnom vremenu dok je app otvoren, a pošiljaocu istekne poslije 10s.
 */
class PozivnicaRepository {

    private val db = FirebaseProvider.db
    private val invites get() = db.collection(FirestoreCollections.MATCH_INVITES)

    /** Šalje poziv prijatelju; vraća id poziva (za praćenje i otkazivanje). */
    suspend fun posalji(myUid: String, myName: String, toUid: String): String {
        val ref = invites.document()
        ref.set(mapOf(
            "fromUid" to myUid,
            "fromName" to myName,
            "toUid" to toUid,
            "status" to PozivNaPartiju.PENDING,
            "matchId" to null,
            "createdAt" to System.currentTimeMillis()
        )).await()
        return ref.id
    }

    /** Prati jedan (poslati) poziv - pošiljalac čeka accepted/declined. */
    fun slusajPoziv(inviteId: String, onChange: (PozivNaPartiju?) -> Unit): ListenerRegistration =
        invites.document(inviteId).addSnapshotListener { snap, _ ->
            onChange(snap?.let { parse(it.id, it.data) })
        }

    /**
     * Sluša dolazne pozive za ulogovanog igrača (status pending). Prikazuju se
     * samo svježi pozivi (mlađi od [MAX_STAROST_MS]) da stari ne iskaču naknadno.
     */
    fun slusajDolazne(myUid: String, onInvite: (PozivNaPartiju) -> Unit): ListenerRegistration =
        invites.whereEqualTo("toUid", myUid)
            .whereEqualTo("status", PozivNaPartiju.PENDING)
            .addSnapshotListener { snaps, _ ->
                val sada = System.currentTimeMillis()
                snaps?.documents.orEmpty()
                    .mapNotNull { parse(it.id, it.data) }
                    .filter { sada - it.createdAt < MAX_STAROST_MS }
                    .maxByOrNull { it.createdAt }
                    ?.let(onInvite)
            }

    suspend fun otkazi(inviteId: String) = postaviStatus(inviteId, PozivNaPartiju.CANCELLED)

    suspend fun odbij(inviteId: String) = postaviStatus(inviteId, PozivNaPartiju.DECLINED)

    /**
     * Primalac prihvata poziv: kreira prijateljsku partiju (pošiljalac je player1)
     * i upiše matchId u poziv. Vraća matchId za ulazak u meč.
     */
    suspend fun prihvati(poziv: PozivNaPartiju, myUid: String, myName: String): String {
        val matchId = MultiplayerRepository()
            .createFriendlyPartija(poziv.fromUid, poziv.fromName, myUid, myName)
        invites.document(poziv.id).update(
            mapOf("status" to PozivNaPartiju.ACCEPTED, "matchId" to matchId)
        ).await()
        return matchId
    }

    private suspend fun postaviStatus(inviteId: String, status: String) {
        runCatching { invites.document(inviteId).update("status", status).await() }
    }

    private fun parse(id: String, d: Map<String, Any?>?): PozivNaPartiju? {
        d ?: return null
        return PozivNaPartiju(
            id = id,
            fromUid = d["fromUid"] as? String ?: return null,
            fromName = d["fromName"] as? String ?: "Prijatelj",
            toUid = d["toUid"] as? String ?: return null,
            status = d["status"] as? String ?: PozivNaPartiju.PENDING,
            matchId = d["matchId"] as? String,
            createdAt = (d["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }

    companion object {
        /** Pozivi stariji od ovoga se ignorišu (10s rok za odgovor + rezerva). */
        const val MAX_STAROST_MS = 15_000L
    }
}
