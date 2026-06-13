package com.example.slagalica.model

data class MatchState(
    val id: String,
    val player1Id: String,
    val player1Name: String,
    val player2Id: String,
    val player2Name: String,
    val gameType: String,
    val status: String,
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

data class RoundState(
    val gameType: String,
    val roundNumber: Int,
    val starterId: String,
    val config: Map<String, Any?>,
    val p1Sub: Map<String, Any?>?,
    val p2Sub: Map<String, Any?>?,
    val p1Points: Int,
    val p2Points: Int,
    val resolved: Boolean,

    val p1Live: List<String> = emptyList(),
    val p2Live: List<String> = emptyList(),

    val raw: Map<String, Any?> = emptyMap()
) {
    val bothSubmitted: Boolean get() = p1Sub != null && p2Sub != null

    fun skockoSecret(): List<Int> =
        (config["secret"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

    fun skockoGuesses(sub: Map<String, Any?>?): List<List<Int>> {
        val raw = (sub?.get("guesses") as? List<*>) ?: return emptyList()
        return raw.mapNotNull { row ->
            (row as? String)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        }
    }

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

    fun kzzOdgovori(sub: Map<String, Any?>?): List<KzzOdgovor> =
        (sub?.get("odgovori") as? List<*>)?.mapNotNull {
            (it as? String)?.let(KzzOdgovor::decode)
        } ?: emptyList()

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

    fun spojniceParovi(sub: Map<String, Any?>?): List<Pair<Int, Int>> =
        (sub?.get("parovi") as? List<*>)?.mapNotNull { parseSpojnicaPar(it as? String) }
            ?: emptyList()

    fun spojniceLiveParovi(isP1: Boolean): List<Pair<Int, Int>> =
        (if (isP1) p1Live else p2Live).mapNotNull { parseSpojnicaPar(it) }

    private fun parseSpojnicaPar(s: String?): Pair<Int, Int>? {
        val delovi = s?.split(",") ?: return null
        val levi = delovi.getOrNull(0)?.toIntOrNull() ?: return null
        val desni = delovi.getOrNull(1)?.toIntOrNull() ?: return null
        return levi to desni
    }

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

    fun asocTurnUid(): String = raw["turnUid"] as? String ?: ""

    fun asocMozeDaPogadja(): Boolean = raw["mozeDaPogadja"] == true

    fun asocOtvorena(): List<Pair<Int, Int>> =
        ((raw["otvorena"] as? List<*>) ?: emptyList<Any>()).mapNotNull {
            parseSpojnicaPar(it as? String)
        }

    fun asocReseneKolone(): Map<Int, String> =
        ((raw["reseneKolone"] as? Map<*, *>) ?: emptyMap<Any, Any>()).entries
            .mapNotNull { e ->
                val col = (e.key as? String)?.toIntOrNull() ?: return@mapNotNull null
                val uid = e.value as? String ?: return@mapNotNull null
                col to uid
            }.toMap()

    fun asocResenoFinalnoUid(): String? = raw["resenoFinalnoUid"] as? String

    fun asocZavrsena(): Boolean = raw["zavrsena"] == true

    @Suppress("UNCHECKED_CAST")
    fun asocPoslednjiPokusaj(): Map<String, Any?>? =
        raw["poslednjiPokusaj"] as? Map<String, Any?>

    fun korakTarget(): String = config["word"] as? String ?: ""

    fun korakHints(): List<String> =
        (config["hints"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    fun mojBrojTarget(): Int = (config["target"] as? Number)?.toInt() ?: 0

    fun mojBrojNumbers(): List<Int> =
        (config["numbers"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
}
