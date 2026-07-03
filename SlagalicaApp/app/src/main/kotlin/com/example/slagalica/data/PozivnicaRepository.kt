package com.example.slagalica.data

import com.example.slagalica.model.PozivNaPartiju
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class PozivnicaRepository {

    private val db = FirebaseProvider.db
    private val invites get() = db.collection(FirestoreCollections.MATCH_INVITES)
    private val notifikacije = NotifikacijeRepository()

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

        runCatching {
            notifikacije.addFor(
                toUid,
                com.example.slagalica.model.AppNotification(
                    id = "",
                    title = "🎮 Poziv na partiju",
                    content = "$myName vas poziva na prijateljsku partiju!",
                    category = com.example.slagalica.model.NotificationCategory.OTHER,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
        return ref.id
    }

    fun slusajPoziv(inviteId: String, onChange: (PozivNaPartiju?) -> Unit): ListenerRegistration =
        invites.document(inviteId).addSnapshotListener { snap, _ ->
            onChange(snap?.let { parse(it.id, it.data) })
        }

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
        const val MAX_STAROST_MS = 15_000L
    }
}
