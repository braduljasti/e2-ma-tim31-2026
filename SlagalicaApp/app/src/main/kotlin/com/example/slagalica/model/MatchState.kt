package com.example.slagalica.model

/**
 * Stanje meča kako ga klijent vidi (isparsirano iz Firestore dokumenta
 * matches/{id}). Polja se sinhronizuju u realnom vremenu između dva telefona.
 */
data class MatchState(
    val id: String,
    val player1Id: String,
    val player1Name: String,
    val player2Id: String,
    val player2Name: String,
    val gameType: String,          // "Skocko" | "Kzz" | "Spojnice" | "Asocijacije"
    val status: String,            // "in_progress" | "finished"
    val currentRoundIndex: Int,
    val rounds: List<RoundState>,
    val player1Score: Int,
    val player2Score: Int,
    val winnerId: String?
) {
    val finished: Boolean get() = status == "finished"
    val currentRound: RoundState? get() = rounds.getOrNull(currentRoundIndex)

    fun isPlayer1(uid: String) = uid == player1Id
    fun opponentName(uid: String) = if (isPlayer1(uid)) player2Name else player1Name
    fun myScore(uid: String) = if (isPlayer1(uid)) player1Score else player2Score
    fun opponentScore(uid: String) = if (isPlayer1(uid)) player2Score else player1Score
}

/**
 * Jedna runda u meču. `config` je tajna konfiguracija (Skočko: secret kombinacija),
 * a `p1Sub`/`p2Sub` su potezi igrača (null dok igrač nije odigrao).
 */
data class RoundState(
    val gameType: String,          // "Skocko" | "Kzz" | "Spojnice" | "Asocijacije"
    val roundNumber: Int,
    val starterId: String,
    val config: Map<String, Any?>,
    val p1Sub: Map<String, Any?>?,
    val p2Sub: Map<String, Any?>?,
    val p1Points: Int,
    val p2Points: Int,
    val resolved: Boolean,
    // Potezi koje igrač objavljuje UŽIVO u toku svoje faze (npr. Spojnice:
    // "levi,desni" po pokušaju) - da protivnik može da posmatra igru u realnom vremenu.
    val p1Live: List<String> = emptyList(),
    val p2Live: List<String> = emptyList(),
    // Kompletna sirova mapa runde iz Firestore-a - za igre čije ŽIVO stanje
    // živi u rundi (Asocijacije: turnUid, otvorena polja, rešene kolone...).
    val raw: Map<String, Any?> = emptyMap()
) {
    val bothSubmitted: Boolean get() = p1Sub != null && p2Sub != null

    /** Skočko tajna kombinacija kao ordinali. */
    fun skockoSecret(): List<Int> =
        (config["secret"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

    /** Pokušaji igrača (svaki pokušaj je string "0,3,1,2"). */
    fun skockoGuesses(sub: Map<String, Any?>?): List<List<Int>> {
        val raw = (sub?.get("guesses") as? List<*>) ?: return emptyList()
        return raw.mapNotNull { row ->
            (row as? String)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        }
    }

    /** Ko zna zna: pitanja runde iz konfiguracije (ista za oba igrača). */
    fun kzzPitanja(): List<KzzPitanje> =
        (config["pitanja"] as? List<*>)?.mapNotNull { p ->
            val m = p as? Map<*, *> ?: return@mapNotNull null
            runCatching {
                KzzPitanje(
                    tekst = m["tekst"] as String,
                    odgovori = (m["odgovori"] as List<*>).map { it as String },
                    tacanIndex = (m["tacanIndex"] as Number).toInt()
                )
            }.getOrNull()
        } ?: emptyList()

    /** Ko zna zna: odgovori igrača (svaki je string "indeks,vremeMs"). */
    fun kzzOdgovori(sub: Map<String, Any?>?): List<KzzOdgovor> =
        (sub?.get("odgovori") as? List<*>)?.mapNotNull {
            (it as? String)?.let(KzzOdgovor::decode)
        } ?: emptyList()

    /** Spojnice: podaci runde iz konfiguracije (isti za oba igrača). */
    fun spojniceRunda(): SpojniceRundaPodaci? = runCatching {
        SpojniceRundaPodaci(
            kriterijum = config["kriterijum"] as String,
            leviPojmovi = (config["levi"] as List<*>).map { it as String },
            desniPojmovi = (config["desni"] as List<*>).map { it as String },
            tacneVeze = (config["veze"] as Map<*, *>).entries.associate {
                (it.key as String).toInt() to (it.value as Number).toInt()
            }
        )
    }.getOrNull()

    /** Spojnice: pokušaji igrača (svaki je string "leviIndeks,desniIndeks"). */
    fun spojniceParovi(sub: Map<String, Any?>?): List<Pair<Int, Int>> =
        (sub?.get("parovi") as? List<*>)?.mapNotNull { parseSpojnicaPar(it as? String) }
            ?: emptyList()

    /** Spojnice: potezi koje igrač objavljuje uživo dok igra svoju fazu. */
    fun spojniceLiveParovi(isP1: Boolean): List<Pair<Int, Int>> =
        (if (isP1) p1Live else p2Live).mapNotNull { parseSpojnicaPar(it) }

    private fun parseSpojnicaPar(s: String?): Pair<Int, Int>? {
        val delovi = s?.split(",") ?: return null
        val levi = delovi.getOrNull(0)?.toIntOrNull() ?: return null
        val desni = delovi.getOrNull(1)?.toIntOrNull() ?: return null
        return levi to desni
    }

    // ===== ASOCIJACIJE: konfiguracija + živo stanje table =====

    /** Asocijacije: podaci runde iz konfiguracije (isti za oba igrača). */
    fun asocRunda(): AsocijacijeRundaPodaci? = runCatching {
        val kolone = config["kolone"] as Map<*, *>
        AsocijacijeRundaPodaci(
            polja = listOf("A", "B", "C", "D").map { k ->
                (kolone[k] as List<*>).map { it as String }
            },
            resenjaKolona = (config["resenjaKolona"] as List<*>).map { it as String },
            finalnoResenje = config["finalnoResenje"] as String
        )
    }.getOrNull()

    /** Ko je trenutno na potezu. */
    fun asocTurnUid(): String = raw["turnUid"] as? String ?: ""

    /** Da li igrač na potezu sme da pogađa (otvorio je polje / u nizu je pogodaka). */
    fun asocMozeDaPogadja(): Boolean = raw["mozeDaPogadja"] == true

    /** Otvorena polja kao (kolona, red) parovi. */
    fun asocOtvorena(): List<Pair<Int, Int>> =
        ((raw["otvorena"] as? List<*>) ?: emptyList<Any>()).mapNotNull {
            parseSpojnicaPar(it as? String)
        }

    /** Rešene kolone: indeks kolone -> uid igrača koji ju je rešio. */
    fun asocReseneKolone(): Map<Int, String> =
        ((raw["reseneKolone"] as? Map<*, *>) ?: emptyMap<Any, Any>()).entries
            .mapNotNull { e ->
                val col = (e.key as? String)?.toIntOrNull() ?: return@mapNotNull null
                val uid = e.value as? String ?: return@mapNotNull null
                col to uid
            }.toMap()

    /** Uid igrača koji je pogodio konačno rešenje (null ako niko nije). */
    fun asocResenoFinalnoUid(): String? = raw["resenoFinalnoUid"] as? String

    /** Da li je igranje runde gotovo (finalno pogođeno ili isteklo vreme). */
    fun asocZavrsena(): Boolean = raw["zavrsena"] == true

    /** Poslednji pokušaj pogađanja: {uid, cilj ("A".."D"/"F"), tekst, tacno, ts}. */
    @Suppress("UNCHECKED_CAST")
    fun asocPoslednjiPokusaj(): Map<String, Any?>? =
        raw["poslednjiPokusaj"] as? Map<String, Any?>
}
