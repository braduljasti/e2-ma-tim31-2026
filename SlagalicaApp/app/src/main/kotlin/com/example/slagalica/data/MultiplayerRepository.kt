package com.example.slagalica.data

import com.example.slagalica.model.AsocijacijeKonstante
import com.example.slagalica.model.KzzKonstante
import com.example.slagalica.model.KzzOdgovor
import com.example.slagalica.model.MatchState
import com.example.slagalica.model.RoundState
import com.example.slagalica.model.SpojniceKonstante
import com.google.firebase.firestore.DocumentSnapshot
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
    private val gameData = GameDataRepository()
    private val matchmaking get() = db.collection("matchmaking")
    private val matches get() = db.collection("matches")

    companion object {
        // Vrijednosti gameType polja u matchmaking tiketu i rundama meča
        const val GAME_SKOCKO = "Skocko"
        const val GAME_KZZ = "Kzz"
        const val GAME_SPOJNICE = "Spojnice"
        const val GAME_ASOCIJACIJE = "Asocijacije"
    }

    // ===== MATCHMAKING =====

    /**
     * Pokušava da se odmah spoji sa nekim ko čeka ISTU igru. Vraća matchId ili null.
     * Podaci rundi (pitanja i sl.) se čitaju iz baze PRIJE transakcije, jer
     * Firestore transakcija ne smije da radi dodatne upite ka drugim kolekcijama.
     */
    suspend fun tryJoin(myUid: String, myName: String, gameType: String): String? {
        val candidate = matchmaking
            .whereEqualTo("status", "waiting")
            .get().await()
            .documents.firstOrNull {
                it.getString("uid") != myUid && it.getString("gameType") == gameType
            }
            ?: return null

        val matchId = matches.document().id
        val ticketRef = candidate.reference
        val rounds = buildRounds(gameType, candidate.getString("uid")!!, myUid)

        return db.runTransaction<String?> { tx ->
            val snap = tx.get(ticketRef)
            if (snap.getString("status") != "waiting") return@runTransaction null
            val p1 = snap.getString("uid")!!
            val p1Name = snap.getString("name") ?: "Igrač"
            tx.set(matches.document(matchId), buildMatchData(p1, p1Name, myUid, myName, gameType, rounds))
            tx.update(ticketRef, mapOf("status" to "matched", "matchId" to matchId))
            matchId
        }.await()
    }

    /** Kreira sopstveni "waiting" tiket i sluša dok ga neko ne upari. */
    fun createTicketAndWait(
        myUid: String,
        myName: String,
        gameType: String,
        onMatched: (String) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val ref = matchmaking.document(myUid)
        ref.set(mapOf(
            "uid" to myUid, "name" to myName, "status" to "waiting",
            "gameType" to gameType,
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

    // ===== KREIRANJE MEČA =====

    /**
     * Pravi runde za izabranu igru. Konfiguracija (pitanja, tajna kombinacija...)
     * se UGRAĐUJE u dokument meča da bi oba igrača garantovano vidjela iste podatke.
     */
    private suspend fun buildRounds(gameType: String, p1: String, p2: String): List<Map<String, Any?>> =
        when (gameType) {
            // KZZ: jedna runda (po spec-u), oba igrača istovremeno odgovaraju ista pitanja
            GAME_KZZ -> {
                val pitanja = gameData.nasumicnaKzzPitanja(KzzKonstante.BROJ_PITANJA)
                listOf(roundMap(GAME_KZZ, 1, p1, mapOf(
                    "pitanja" to pitanja.map {
                        mapOf("tekst" to it.tekst, "odgovori" to it.odgovori, "tacanIndex" to it.tacanIndex)
                    }
                )))
            }
            // Spojnice: 2 runde, svaku počinje po jedan igrač; protivnik dobija
            // pojmove koje starter nije tačno povezao
            GAME_SPOJNICE -> {
                val runde = gameData.nasumicneSpojnice(SpojniceKonstante.BROJ_RUNDI)
                runde.mapIndexed { i, r ->
                    roundMap(GAME_SPOJNICE, i + 1, if (i == 0) p1 else p2, mapOf(
                        "kriterijum" to r.kriterijum,
                        "levi" to r.leviPojmovi,
                        "desni" to r.desniPojmovi,
                        "veze" to r.tacneVeze.entries.associate { e -> e.key.toString() to e.value }
                    ))
                }
            }
            // Asocijacije: 2 runde na zajedničkoj tabli, igrači se smenjuju potez
            // po potez; živo stanje table je u samoj rundi (turnUid, otvorena...)
            GAME_ASOCIJACIJE -> {
                val runde = gameData.nasumicneAsocijacije(AsocijacijeKonstante.BROJ_RUNDI)
                runde.mapIndexed { i, r ->
                    val starter = if (i == 0) p1 else p2
                    roundMap(GAME_ASOCIJACIJE, i + 1, starter, mapOf(
                        "kolone" to mapOf(
                            "A" to r.polja[0], "B" to r.polja[1],
                            "C" to r.polja[2], "D" to r.polja[3]
                        ),
                        "resenjaKolona" to r.resenjaKolona,
                        "finalnoResenje" to r.finalnoResenje
                    )) + mapOf(
                        "turnUid" to starter,
                        "mozeDaPogadja" to false,
                        "otvorena" to emptyList<String>(),
                        "reseneKolone" to emptyMap<String, String>(),
                        "resenoFinalnoUid" to null,
                        "zavrsena" to false
                    )
                }
            }
            // Skočko: 2 runde, svaku počinje po jedan igrač
            else -> (1..2).map { r ->
                roundMap(GAME_SKOCKO, r, if (r == 1) p1 else p2,
                    mapOf("secret" to GameLogic.newSkockoSecret()))
            }
        }

    private fun roundMap(
        gameType: String,
        roundNumber: Int,
        starterId: String,
        config: Map<String, Any?>
    ): Map<String, Any?> = mapOf(
        "gameType" to gameType,
        "roundNumber" to roundNumber,
        "starterId" to starterId,
        "config" to config,
        "p1Sub" to null,
        "p2Sub" to null,
        "p1Points" to 0,
        "p2Points" to 0,
        "resolved" to false
    )

    private fun buildMatchData(
        p1: String, p1Name: String, p2: String, p2Name: String,
        gameType: String, rounds: List<Map<String, Any?>>
    ): Map<String, Any?> = mapOf(
        "player1Id" to p1, "player1Name" to p1Name,
        "player2Id" to p2, "player2Name" to p2Name,
        "gameType" to gameType,
        "status" to "in_progress",
        "currentRoundIndex" to 0,
        "rounds" to rounds,
        "player1Score" to 0, "player2Score" to 0,
        "winnerId" to null,
        "createdAt" to FieldValue.serverTimestamp()
    )

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
                resolved = r["resolved"] as? Boolean ?: false,
                p1Live = (r["p1Live"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                p2Live = (r["p2Live"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                raw = r
            )
        }
        return MatchState(
            id = id,
            player1Id = d["player1Id"] as? String ?: "",
            player1Name = d["player1Name"] as? String ?: "Igrač 1",
            player2Id = d["player2Id"] as? String ?: "",
            player2Name = d["player2Name"] as? String ?: "Igrač 2",
            gameType = d["gameType"] as? String ?: GAME_SKOCKO,
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
    suspend fun submitSkocko(matchId: String, isP1: Boolean, guesses: List<List<Int>>) =
        submitSub(matchId, isP1, mapOf("guesses" to guesses.map { it.joinToString(",") }))

    /** Upisuje odgovore (Ko zna zna): svaki odgovor je string "indeks,vremeMs". */
    suspend fun submitKzz(matchId: String, isP1: Boolean, odgovori: List<KzzOdgovor>) =
        submitSub(matchId, isP1, mapOf("odgovori" to odgovori.map { it.encode() }))

    /** Upisuje pokušaje (Spojnice): svaki par je string "leviIndeks,desniIndeks". */
    suspend fun submitSpojnice(matchId: String, isP1: Boolean, parovi: List<Pair<Int, Int>>) =
        submitSub(matchId, isP1, mapOf("parovi" to parovi.map { "${it.first},${it.second}" }))

    /**
     * Objavljuje jedan potez UŽIVO u toku faze (Spojnice) - protivnik ga odmah
     * vidi kroz snapshot listener i može da posmatra igru u realnom vremenu.
     * `roundIndex` štiti od kasnih upisa: ako je runda u međuvremenu bodovana
     * i meč otišao dalje, potez se tiho odbacuje.
     */
    suspend fun spojniceLivePotez(matchId: String, isP1: Boolean, roundIndex: Int, par: Pair<Int, Int>) {
        val ref = matches.document(matchId)
        db.runTransaction<Void?> { tx ->
            val snap = tx.get(ref)
            @Suppress("UNCHECKED_CAST")
            val rounds = (snap.get("rounds") as? MutableList<MutableMap<String, Any?>>)
                ?: return@runTransaction null
            val idx = (snap.getLong("currentRoundIndex") ?: 0L).toInt()
            if (idx != roundIndex) return@runTransaction null
            val round = rounds.getOrNull(idx) ?: return@runTransaction null
            val key = if (isP1) "p1Live" else "p2Live"
            val live = ((round[key] as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                .toMutableList()
            live.add("${par.first},${par.second}")
            round[key] = live
            tx.update(ref, "rounds", rounds)
            null
        }.await()
    }

    /** Zajednički upis poteza za tekuću rundu - upisuje samo ako igrač već nije odigrao. */
    private suspend fun submitSub(matchId: String, isP1: Boolean, sub: Map<String, Any?>) {
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
                round[key] = sub
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

            // Bodovanje zavisi od igre u tekućoj rundi
            val gameType = round["gameType"] as? String ?: GAME_SKOCKO
            val (p1Pts, p2Pts) = when (gameType) {
                GAME_KZZ -> resolveKzzRound(round, p1Sub, p2Sub)
                GAME_SPOJNICE -> resolveSpojniceRound(snap.getString("player1Id"), round, p1Sub, p2Sub)
                else -> resolveSkockoRound(snap.getString("player1Id"), round, p1Sub, p2Sub)
            }

            round["p1Points"] = p1Pts
            round["p2Points"] = p2Pts
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

    // ===== BODOVANJE POJEDINAČNIH IGARA (poziva host iz transakcije) =====

    /** Skočko: bodovanje zavisi od toga ko je starter runde. Vraća (p1, p2) bodove. */
    private fun resolveSkockoRound(
        player1Id: String?,
        round: Map<String, Any?>,
        p1Sub: Map<*, *>,
        p2Sub: Map<*, *>
    ): Pair<Int, Int> {
        val secret = ((round["config"] as? Map<*, *>)?.get("secret") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val starterIsP1 = (round["starterId"] as? String ?: "") == player1Id

        fun parseGuesses(sub: Map<*, *>?): List<List<Int>> =
            (sub?.get("guesses") as? List<*>)?.mapNotNull { row ->
                (row as? String)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
            } ?: emptyList()

        val starterGuesses = parseGuesses(if (starterIsP1) p1Sub else p2Sub)
        val oppGuesses = parseGuesses(if (starterIsP1) p2Sub else p1Sub)
        val (starterPts, oppPts) = GameLogic.resolveSkocko(secret, starterGuesses, oppGuesses)
        return if (starterIsP1) starterPts to oppPts else oppPts to starterPts
    }

    /** Spojnice: starter povezuje prvi, protivnik dobija preostale. Vraća (p1, p2). */
    private fun resolveSpojniceRound(
        player1Id: String?,
        round: Map<String, Any?>,
        p1Sub: Map<*, *>,
        p2Sub: Map<*, *>
    ): Pair<Int, Int> {
        val veze = ((round["config"] as? Map<*, *>)?.get("veze") as? Map<*, *>)?.entries
            ?.mapNotNull { e ->
                val levi = (e.key as? String)?.toIntOrNull() ?: return@mapNotNull null
                val desni = (e.value as? Number)?.toInt() ?: return@mapNotNull null
                levi to desni
            }?.toMap() ?: emptyMap()
        val starterIsP1 = (round["starterId"] as? String ?: "") == player1Id

        fun parseParovi(sub: Map<*, *>?): List<Pair<Int, Int>> =
            (sub?.get("parovi") as? List<*>)?.mapNotNull {
                val delovi = (it as? String)?.split(",") ?: return@mapNotNull null
                val levi = delovi.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val desni = delovi.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                levi to desni
            } ?: emptyList()

        val starterParovi = parseParovi(if (starterIsP1) p1Sub else p2Sub)
        val oppParovi = parseParovi(if (starterIsP1) p2Sub else p1Sub)
        val (starterPts, oppPts) = GameLogic.resolveSpojnice(veze, starterParovi, oppParovi)
        return if (starterIsP1) starterPts to oppPts else oppPts to starterPts
    }

    // ===== ASOCIJACIJE: potezi na zajedničkoj tabli =====
    // Svaki potez je transakcija nad rundom; runda se završava pogotkom
    // konačnog rešenja ili istekom vremena, i tada se odmah (u istoj
    // transakciji) boduje i meč pomera dalje - nema posebnog host koraka.

    /** Otvaranje polja - dozvoljeno samo igraču koji je na potezu. */
    suspend fun asocijacijeOtvoriPolje(matchId: String, roundIndex: Int, uid: String, col: Int, row: Int) =
        asocijacijePotez(matchId, roundIndex) { _, _, round ->
            if (round["turnUid"] != uid) return@asocijacijePotez null
            val otvorena = strList(round["otvorena"]).toMutableList()
            val polje = "$col,$row"
            if (polje in otvorena) return@asocijacijePotez null
            otvorena.add(polje)
            round["otvorena"] = otvorena
            round["mozeDaPogadja"] = true
            emptyMap()
        }

    /** Pogađanje rešenja kolone. Tačno -> bodovi i ostaje na potezu; netačno -> potez prelazi. */
    suspend fun asocijacijePogodiKolonu(matchId: String, roundIndex: Int, uid: String, col: Int, guess: String) =
        asocijacijePotez(matchId, roundIndex) { snap, _, round ->
            if (round["turnUid"] != uid || round["mozeDaPogadja"] != true) return@asocijacijePotez null
            val resene = ((round["reseneKolone"] as? Map<*, *>) ?: emptyMap<Any, Any>())
                .entries.associate { it.key.toString() to (it.value as? String ?: "") }
                .toMutableMap()
            if (resene.containsKey(col.toString())) return@asocijacijePotez null

            val resenja = ((round["config"] as? Map<*, *>)?.get("resenjaKolona") as? List<*>)
                ?.mapNotNull { it as? String } ?: return@asocijacijePotez null

            val tacno = GameLogic.asocijacijeTacno(guess, resenja.getOrNull(col) ?: "")
            zabeleziPokusaj(round, uid, "ABCD"[col].toString(), guess, tacno)
            if (tacno) {
                resene[col.toString()] = uid
                round["reseneKolone"] = resene
                // Bodovi po broju otvorenih PRE pogotka, pa se kolona otkriva cela
                val otvorena = strList(round["otvorena"]).toMutableList()
                val otvorenihUKoloni = otvorena.count { it.startsWith("$col,") }
                dodajPoene(snap, round, uid, GameLogic.asocijacijePoeniKolona(otvorenihUKoloni))
                for (row in 0 until AsocijacijeKonstante.POLJA_PO_KOLONI) {
                    val polje = "$col,$row"
                    if (polje !in otvorena) otvorena.add(polje)
                }
                round["otvorena"] = otvorena
            } else {
                predajPotez(snap, round, uid)
            }
            emptyMap()
        }

    /** Pogađanje konačnog rešenja. Tačno -> bodovi i kraj runde; netačno -> potez prelazi. */
    suspend fun asocijacijePogodiFinalno(matchId: String, roundIndex: Int, uid: String, guess: String) =
        asocijacijePotez(matchId, roundIndex) { snap, rounds, round ->
            if (round["turnUid"] != uid || round["mozeDaPogadja"] != true) return@asocijacijePotez null
            if (round["resenoFinalnoUid"] != null) return@asocijacijePotez null
            val finalno = (round["config"] as? Map<*, *>)?.get("finalnoResenje") as? String
                ?: return@asocijacijePotez null

            val tacno = GameLogic.asocijacijeTacno(guess, finalno)
            zabeleziPokusaj(round, uid, "F", guess, tacno)
            if (tacno) {
                round["resenoFinalnoUid"] = uid
                val otvorena = strList(round["otvorena"])
                val resene = (round["reseneKolone"] as? Map<*, *>) ?: emptyMap<Any, Any>()
                val otvorenihPoKoloni = (0..3).map { c -> otvorena.count { it.startsWith("$c,") } }
                val kolonaResena = (0..3).map { resene.containsKey(it.toString()) }
                dodajPoene(snap, round, uid,
                    GameLogic.asocijacijePoeniFinalno(otvorenihPoKoloni, kolonaResena))
                zavrsiAsocRundu(round)
            } else {
                predajPotez(snap, round, uid)
            }
            emptyMap()
        }

    /** Igrač ne želi da pogađa - potez prelazi protivniku. */
    suspend fun asocijacijePropusti(matchId: String, roundIndex: Int, uid: String) =
        asocijacijePotez(matchId, roundIndex) { snap, _, round ->
            if (round["turnUid"] != uid) return@asocijacijePotez null
            predajPotez(snap, round, uid)
            emptyMap()
        }

    /** Isteklo vreme runde (2 min) - bilo koji klijent sme da je zatvori. */
    suspend fun asocijacijeIstekloVreme(matchId: String, roundIndex: Int) =
        asocijacijePotez(matchId, roundIndex) { _, _, round ->
            zavrsiAsocRundu(round)
            emptyMap()
        }

    /**
     * Pomera meč na sledeću rundu (ili ga završava) NAKON pauze za pregled
     * otkrivene table. Zovu je oba klijenta posle 7 sekundi - prva transakcija
     * pobedi, druga vidi pomeren indeks i tiho odustane.
     */
    suspend fun asocijacijeSledecaRunda(matchId: String, roundIndex: Int) {
        val ref = matches.document(matchId)
        db.runTransaction<Void?> { tx ->
            val snap = tx.get(ref)
            if (snap.getString("status") == "finished") return@runTransaction null
            @Suppress("UNCHECKED_CAST")
            val rounds = (snap.get("rounds") as? MutableList<MutableMap<String, Any?>>)
                ?: return@runTransaction null
            val idx = (snap.getLong("currentRoundIndex") ?: 0L).toInt()
            if (idx != roundIndex) return@runTransaction null
            val round = rounds.getOrNull(idx) ?: return@runTransaction null
            if (round["zavrsena"] != true) return@runTransaction null

            val newIndex = idx + 1
            val updates = hashMapOf<String, Any?>("currentRoundIndex" to newIndex)
            if (newIndex >= rounds.size) {
                val p1Total = rounds.sumOf { (it["p1Points"] as? Number)?.toInt() ?: 0 }
                val p2Total = rounds.sumOf { (it["p2Points"] as? Number)?.toInt() ?: 0 }
                updates["player1Score"] = p1Total
                updates["player2Score"] = p2Total
                updates["winnerId"] = when {
                    p1Total > p2Total -> snap.getString("player1Id")
                    p2Total > p1Total -> snap.getString("player2Id")
                    else -> null
                }
                updates["status"] = "finished"
            }
            tx.update(ref, updates)
            null
        }.await()
    }

    /**
     * Zajednički okvir za potez: transakcija + zaštite (meč nije gotov, runda
     * je i dalje tekuća i nije završena). `block` menja rundu i vraća dodatne
     * top-level izmene, ili null da tiho odustane.
     */
    private suspend fun asocijacijePotez(
        matchId: String,
        roundIndex: Int,
        block: (DocumentSnapshot, MutableList<MutableMap<String, Any?>>, MutableMap<String, Any?>) -> Map<String, Any?>?
    ) {
        val ref = matches.document(matchId)
        db.runTransaction<Void?> { tx ->
            val snap = tx.get(ref)
            if (snap.getString("status") == "finished") return@runTransaction null
            @Suppress("UNCHECKED_CAST")
            val rounds = (snap.get("rounds") as? MutableList<MutableMap<String, Any?>>)
                ?: return@runTransaction null
            val idx = (snap.getLong("currentRoundIndex") ?: 0L).toInt()
            if (idx != roundIndex) return@runTransaction null
            val round = rounds.getOrNull(idx) ?: return@runTransaction null
            if (round["zavrsena"] == true) return@runTransaction null

            val extra = block(snap, rounds, round) ?: return@runTransaction null
            val updates = hashMapOf<String, Any?>("rounds" to rounds)
            updates.putAll(extra)
            tx.update(ref, updates)
            null
        }.await()
    }

    /** Upisuje poene igraču u tekuću rundu (bodovi se računaju u trenutku pogotka). */
    private fun dodajPoene(snap: DocumentSnapshot, round: MutableMap<String, Any?>, uid: String, poeni: Int) {
        val key = if (uid == snap.getString("player1Id")) "p1Points" else "p2Points"
        round[key] = ((round[key] as? Number)?.toInt() ?: 0) + poeni
    }

    private fun predajPotez(snap: DocumentSnapshot, round: MutableMap<String, Any?>, uid: String) {
        val p1 = snap.getString("player1Id")
        round["turnUid"] = if (uid == p1) snap.getString("player2Id") else p1
        // Ako su sva polja već otvorena, novi igrač nema šta da otvori -
        // odmah dobija pravo pogađanja (inače bi se igra zaglavila do isteka vremena)
        val svaOtvorena = strList(round["otvorena"]).size >=
                AsocijacijeKonstante.BROJ_KOLONA * AsocijacijeKonstante.POLJA_PO_KOLONI
        round["mozeDaPogadja"] = svaOtvorena
    }

    /**
     * Zatvara IGRANJE runde: otkriva celu tablu i označava rundu završenom.
     * Meč se ne pomera odmah - klijenti 7s prikazuju otkrivena rešenja,
     * a zatim pozivaju asocijacijeSledecaRunda().
     */
    private fun zavrsiAsocRundu(round: MutableMap<String, Any?>) {
        round["zavrsena"] = true
        round["resolved"] = true
        round["mozeDaPogadja"] = false
        round["otvorena"] = (0 until AsocijacijeKonstante.BROJ_KOLONA).flatMap { col ->
            (0 until AsocijacijeKonstante.POLJA_PO_KOLONI).map { row -> "$col,$row" }
        }
    }

    /** Beleži poslednji pokušaj pogađanja - protivnik ga prikazuje na par sekundi. */
    private fun zabeleziPokusaj(
        round: MutableMap<String, Any?>,
        uid: String,
        cilj: String,        // "A".."D" za kolonu, "F" za konačno rešenje
        tekst: String,
        tacno: Boolean
    ) {
        round["poslednjiPokusaj"] = mapOf(
            "uid" to uid, "cilj" to cilj, "tekst" to tekst.trim(),
            "tacno" to tacno, "ts" to System.currentTimeMillis()
        )
    }

    private fun strList(v: Any?): List<String> =
        (v as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    /** Ko zna zna: oba igrača odgovaraju ista pitanja; brži tačan nosi bodove. */
    private fun resolveKzzRound(
        round: Map<String, Any?>,
        p1Sub: Map<*, *>,
        p2Sub: Map<*, *>
    ): Pair<Int, Int> {
        val tacniIndeksi = ((round["config"] as? Map<*, *>)?.get("pitanja") as? List<*>)
            ?.mapNotNull { ((it as? Map<*, *>)?.get("tacanIndex") as? Number)?.toInt() }
            ?: emptyList()

        fun parseOdgovori(sub: Map<*, *>?): List<KzzOdgovor> =
            (sub?.get("odgovori") as? List<*>)?.mapNotNull {
                (it as? String)?.let(KzzOdgovor::decode)
            } ?: emptyList()

        return GameLogic.resolveKzz(tacniIndeksi, parseOdgovori(p1Sub), parseOdgovori(p2Sub))
    }
}
