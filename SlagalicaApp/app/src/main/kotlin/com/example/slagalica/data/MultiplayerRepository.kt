package com.example.slagalica.data

import com.example.slagalica.model.MatchState
import com.example.slagalica.model.RoundState
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Realtime 1-na-1 preko Firestore-a, bez ijednog servera.
 *
 * Tok:
 *  1) matchmaking: igrač se prijavi u "matchmaking"; ako neko već čeka - spaja ih u meč.
 *  2) oba telefona slušaju matches/{id} u realnom vremenu.
 *  3) svaki igrač upiše svoj potez; kad oba odigraju, HOST (player1) boduje rundu
 *     i pomjera meč naprijed. Drugi telefon samo prati promjene.
 *
 * Napomena: pošto nema servera, tajna kombinacija je u dijeljenom dokumentu
 * (oba je vide). To je svjestan kompromis Firebase-only pristupa - dovoljno za
 * projekat; za pravi anti-cheat bio bi potreban server.
 */
class MultiplayerRepository {

    private val db = FirebaseProvider.db
    private val matchmaking get() = db.collection("matchmaking")
    private val matches get() = db.collection("matches")

    // ===== MATCHMAKING =====

    /** Pokušava da se odmah spoji sa nekim ko čeka. Vraća matchId ili null. */
    suspend fun tryJoin(myUid: String, myName: String): String? {
        val candidate = matchmaking
            .whereEqualTo("status", "waiting")
            .get().await()
            .documents.firstOrNull { it.getString("uid") != myUid }
            ?: return null

        val matchId = matches.document().id
        val ticketRef = candidate.reference

        return db.runTransaction<String?> { tx ->
            val snap = tx.get(ticketRef)
            if (snap.getString("status") != "waiting") return@runTransaction null
            val p1 = snap.getString("uid")!!
            val p1Name = snap.getString("name") ?: "Igrač"
            tx.set(matches.document(matchId), buildMatchData(p1, p1Name, myUid, myName))
            tx.update(ticketRef, mapOf("status" to "matched", "matchId" to matchId))
            matchId
        }.await()
    }

    /** Kreira sopstveni "waiting" tiket i sluša dok ga neko ne upari. */
    fun createTicketAndWait(
        myUid: String,
        myName: String,
        onMatched: (String) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val ref = matchmaking.document(myUid)
        ref.set(mapOf(
            "uid" to myUid, "name" to myName, "status" to "waiting",
            "matchId" to null, "createdAt" to FieldValue.serverTimestamp()
        ))
        return ref.addSnapshotListener { snap, err ->
            if (err != null) { onError(err); return@addSnapshotListener }
            val status = snap?.getString("status")
            val matchId = snap?.getString("matchId")
            if (status == "matched" && matchId != null) onMatched(matchId)
        }
    }

    suspend fun cancelMatchmaking(myUid: String) {
        runCatching { matchmaking.document(myUid).delete().await() }
    }

    // ===== KREIRANJE MEČA (demo: Skočko, 2 runde) =====

    private fun buildMatchData(p1: String, p1Name: String, p2: String, p2Name: String): Map<String, Any?> {
        val rounds = (1..2).map { round ->
            mapOf(
                "gameType" to "Skocko",
                "roundNumber" to round,
                "starterId" to if (round == 1) p1 else p2,
                "config" to mapOf("secret" to GameLogic.newSkockoSecret()),
                "p1Sub" to null,
                "p2Sub" to null,
                "p1Points" to 0,
                "p2Points" to 0,
                "resolved" to false
            )
        }
        return mapOf(
            "player1Id" to p1, "player1Name" to p1Name,
            "player2Id" to p2, "player2Name" to p2Name,
            "status" to "in_progress",
            "currentRoundIndex" to 0,
            "rounds" to rounds,
            "player1Score" to 0, "player2Score" to 0,
            "winnerId" to null,
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    // ===== SINHRONIZACIJA =====

    fun listenMatch(matchId: String, onUpdate: (MatchState) -> Unit): ListenerRegistration {
        return matches.document(matchId).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener
            onUpdate(parseMatch(matchId, snap.data ?: return@addSnapshotListener))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMatch(id: String, d: Map<String, Any?>): MatchState {
        val rawRounds = (d["rounds"] as? List<Map<String, Any?>>) ?: emptyList()
        val rounds = rawRounds.map { r ->
            RoundState(
                gameType = r["gameType"] as? String ?: "Skocko",
                roundNumber = (r["roundNumber"] as? Number)?.toInt() ?: 1,
                starterId = r["starterId"] as? String ?: "",
                config = (r["config"] as? Map<String, Any?>) ?: emptyMap(),
                p1Sub = r["p1Sub"] as? Map<String, Any?>,
                p2Sub = r["p2Sub"] as? Map<String, Any?>,
                p1Points = (r["p1Points"] as? Number)?.toInt() ?: 0,
                p2Points = (r["p2Points"] as? Number)?.toInt() ?: 0,
                resolved = r["resolved"] as? Boolean ?: false
            )
        }
        return MatchState(
            id = id,
            player1Id = d["player1Id"] as? String ?: "",
            player1Name = d["player1Name"] as? String ?: "Igrač 1",
            player2Id = d["player2Id"] as? String ?: "",
            player2Name = d["player2Name"] as? String ?: "Igrač 2",
            status = d["status"] as? String ?: "in_progress",
            currentRoundIndex = (d["currentRoundIndex"] as? Number)?.toInt() ?: 0,
            rounds = rounds,
            player1Score = (d["player1Score"] as? Number)?.toInt() ?: 0,
            player2Score = (d["player2Score"] as? Number)?.toInt() ?: 0,
            winnerId = d["winnerId"] as? String
        )
    }

    // ===== POTEZI =====

    /** Upisuje pokušaje (Skočko) trenutnog igrača za tekuću rundu. */
    suspend fun submitSkocko(matchId: String, isP1: Boolean, guesses: List<List<Int>>) {
        val ref = matches.document(matchId)
        db.runTransaction<Void?> { tx ->
            val snap = tx.get(ref)
            @Suppress("UNCHECKED_CAST")
            val rounds = (snap.get("rounds") as? MutableList<MutableMap<String, Any?>>)
                ?: return@runTransaction null
            val idx = (snap.getLong("currentRoundIndex") ?: 0L).toInt()
            val round = rounds.getOrNull(idx) ?: return@runTransaction null
            val key = if (isP1) "p1Sub" else "p2Sub"
            if (round[key] == null) {
                round[key] = mapOf("guesses" to guesses.map { it.joinToString(",") })
                tx.update(ref, "rounds", rounds)
            }
            null
        }.await()
    }

    /**
     * HOST (player1) zove na svaki update: ako su oba odigrala tekuću rundu,
     * boduje je, upisuje poene, pomjera meč i na kraju proglašava pobjednika.
     */
    suspend fun hostResolveIfReady(matchId: String) {
        val ref = matches.document(matchId)
        db.runTransaction<Void?> { tx ->
            val snap = tx.get(ref)
            if (snap.getString("status") == "finished") return@runTransaction null
            @Suppress("UNCHECKED_CAST")
            val rounds = (snap.get("rounds") as? MutableList<MutableMap<String, Any?>>)
                ?: return@runTransaction null
            val idx = (snap.getLong("currentRoundIndex") ?: 0L).toInt()
            val round = rounds.getOrNull(idx) ?: return@runTransaction null

            val resolved = round["resolved"] as? Boolean ?: false
            val p1Sub = round["p1Sub"] as? Map<*, *>
            val p2Sub = round["p2Sub"] as? Map<*, *>
            if (resolved || p1Sub == null || p2Sub == null) return@runTransaction null

            @Suppress("UNCHECKED_CAST")
            val secret = ((round["config"] as? Map<String, Any?>)?.get("secret") as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
            val starterId = round["starterId"] as? String ?: ""
            val player1Id = snap.getString("player1Id")
            val starterIsP1 = starterId == player1Id

            fun parseGuesses(sub: Map<*, *>?): List<List<Int>> =
                (sub?.get("guesses") as? List<*>)?.mapNotNull { row ->
                    (row as? String)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                } ?: emptyList()

            val starterGuesses = parseGuesses(if (starterIsP1) p1Sub else p2Sub)
            val oppGuesses = parseGuesses(if (starterIsP1) p2Sub else p1Sub)
            val (starterPts, oppPts) = GameLogic.resolveSkocko(secret, starterGuesses, oppGuesses)

            round["p1Points"] = if (starterIsP1) starterPts else oppPts
            round["p2Points"] = if (starterIsP1) oppPts else starterPts
            round["resolved"] = true

            val newIndex = idx + 1
            val updates = hashMapOf<String, Any?>("rounds" to rounds, "currentRoundIndex" to newIndex)

            if (newIndex >= rounds.size) {
                val p1Total = rounds.sumOf { (it["p1Points"] as? Number)?.toInt() ?: 0 }
                val p2Total = rounds.sumOf { (it["p2Points"] as? Number)?.toInt() ?: 0 }
                val winner = when {
                    p1Total > p2Total -> snap.getString("player1Id")
                    p2Total > p1Total -> snap.getString("player2Id")
                    else -> null
                }
                updates["player1Score"] = p1Total
                updates["player2Score"] = p2Total
                updates["winnerId"] = winner
                updates["status"] = "finished"
            }
            tx.update(ref, updates)
            null
        }.await()
    }
}
