package com.example.slagalica.model

enum class NotificationCategory {
    CHAT, RANK, REWARDS, OTHER
}

data class AppNotification(
    val id: String,
    val title: String,
    val content: String,
    val category: NotificationCategory,
    val timestampMs: Long,
    var read: Boolean = false
) {
    fun relativeTime(): String {
        val diffMs = System.currentTimeMillis() - timestampMs
        val diffMin = diffMs / 60_000
        val diffHour = diffMin / 60
        return when {
            diffMin < 1 -> "Upravo sada"
            diffMin < 60 -> "Prije $diffMin min"
            diffHour < 24 -> "Prije $diffHour h"
            else -> "Prije ${diffHour / 24} d"
        }
    }

    fun emoji(): String = when (category) {
        NotificationCategory.CHAT -> "💬"
        NotificationCategory.RANK -> "🏆"
        NotificationCategory.REWARDS -> "🎁"
        NotificationCategory.OTHER -> "🔔"
    }
}

enum class NotificationFilter { ALL, READ, UNREAD }

data class FirebaseUser(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val region: String = "",
    val createdAt: Long = 0L,
    val emailVerified: Boolean = false,

    val avatarId: Int = 1,
    val tokens: Int = 5,
    val stars: Int = 0,          // trenutni balans zvezda (osnova za ligu, može da pada)
    val league: Int = 0,         // indeks lige (0 = nulta)

    // Zvezde osvojene u tekućem ciklusu (rang lista / regioni). Resetuje ih reconcile
    // na promjeni ciklusa - ta logika dolazi uz funkcionalnosti 4/5/6.
    val starsWeekly: Int = 0,
    val starsMonthly: Int = 0,

    // Kumulativno OSVOJENE zvezde (samo rastu) i koliko je tokena već dodijeljeno iz njih.
    // Po spec 3.d.iii: svakih 50 osvojenih zvezda donosi 1 token.
    val lifetimeStars: Int = 0,
    val tokensFromStars: Int = 0,

    // Lazy reconcile (obrada pri pokretanju app-a, bez servera):
    // datum zadnje dnevne dodjele tokena i zadnji viđeni ciklus (za reset zvezda).
    val lastDailyGrant: Long = 0L,
    val lastCycleWeekly: String = "",
    val lastCycleMonthly: String = "",

    // Prisustvo (za "aktivni igrači" u statistici regiona i online status prijatelja).
    val lastSeen: Long = 0L,

    // Rang lista (spec 4) - ciklusi ("W-2026-W27", "M-2026-07") za koje je već isplaćena
    // nagrada za plasman, da ista nagrada ne bi bila isplaćena više puta.
    val rewardedCycles: List<String> = emptyList()
)

/** Poruka u regionalnom četu (spec 8). */
data class ChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestampMs: Long = 0L
)

enum class RangCiklus { NEDELJNI, MESECNI }

/** Red na globalnoj rang listi (spec 4) - izveden direktno iz users.starsWeekly/starsMonthly. */
data class RangListaStavka(
    val uid: String,
    val username: String,
    val league: Int,
    val stars: Int
)

/** Statistika jednog regiona na klik (spec 5.d). */
data class RegionStatistika(
    val naziv: String,
    val emoji: String,
    val registrovani: Int,
    val aktivni: Int,
    val prvaMjesta: Int,
    val drugaMjesta: Int,
    val trecaMjesta: Int
)

/** Ishod lazy reconcile-a (dnevni tokeni + reset ciklusa) za eventualni prikaz korisniku. */
data class ReconcileOutcome(
    val tokensAdded: Int,
    val weeklyReset: Boolean,
    val monthlyReset: Boolean
)

/** Tačka jednog igrača na mapi regiona (spec 5.a). */
data class IgracTacka(
    val username: String,
    val regionNaziv: String,
    val lat: Double,
    val lng: Double,
    val jaSam: Boolean
)

/** Red mjesečne rang liste po regionima (spec 5.b) - zbir zvezda igrača regiona u ciklusu. */
data class RegionRangRed(
    val regionNaziv: String,
    val emoji: String,
    val ukupnoZvezda: Int,
    val brojIgraca: Int,
    val mojRegion: Boolean
)

/** Jedan red u pregledu liga (spec 6) - liga, prag zvezda, dnevni tokeni, da li je trenutna. */
data class LigaRed(
    val liga: Liga,
    val prag: Int,
    val tokeniDan: Int,
    val jeTrenutna: Boolean
)

/** Pregled napredovanja kroz lige za ekran "Lige". */
data class LigaPregled(
    val trenutnaLiga: Liga,
    val stars: Int,
    val sledeciPrag: Int?,        // null ako je igrač u najvišoj ligi
    val progressPercent: Int,     // napredak ka sljedećoj ligi (0..100)
    val redovi: List<LigaRed>
) {
    val doSledece: Int get() = ((sledeciPrag ?: stars) - stars).coerceAtLeast(0)
}

/**
 * Stavka u listi prijatelja / rezultatu pretrage. `jePrijatelj` određuje da li
 * dugme na kartici prikazuje "Dodaj" (false) ili "Ukloni" (true).
 */
data class PrijateljItem(
    val user: FirebaseUser,
    val jePrijatelj: Boolean
)

/**
 * Ishod obrade rezultata partije (applyMatchResult) - vraća se pozivaocu
 * da prikaže dijalog/notifikaciju (npr. "+13 zvezda, prešao si u Srebrnu ligu").
 */
data class MatchRewardOutcome(
    val deltaStars: Int,        // promjena balansa (može biti negativna)
    val newStars: Int,          // novi ukupan balans
    val tokensAwarded: Int,     // koliko tokena dodijeljeno (pragovi od 50 zvezda)
    val oldLeague: Int,
    val newLeague: Int
) {
    val leagueChanged: Boolean get() = oldLeague != newLeague
    val promoted: Boolean get() = newLeague > oldLeague        // napredovao
    val relegated: Boolean get() = newLeague < oldLeague       // ispao
}

enum class GameType(val displayName: String) {
    SKOCKO("Skočko"),
    KORAK_PO_KORAK("Korak po korak"),
    MOJ_BROJ("Moj broj"),
    KO_ZNA_ZNA("Ko zna zna"),
    SPOJNICE("Spojnice"),
    ASOCIJACIJE("Asocijacije")
}

data class GameResult(
    val id: String = "",
    val gameType: String = "",
    val myPoints: Int = 0,
    val opponentPoints: Int = 0,
    val won: Boolean = false,
    val playedAt: Long = 0L,

    val details: Map<String, Long> = emptyMap()
)

data class SkockoAttempt(
    val combination: List<SkockoSymbol>,
    val correctPosition: Int,
    val wrongPosition: Int
)

enum class SkockoSymbol(val emoji: String) {
    SQUARE("■"),
    CIRCLE("●"),
    HEART("♥"),
    TRIANGLE("▲"),
    STAR("★"),
    DIAMOND("◆");

    companion object {
        fun all() = values().toList()
    }
}

data class StepData(
    val id: Long,
    val targetWord: String,
    val hints: List<String>
)

data class MyNumberData(
    val targetNumber: Int,
    val availableNumbers: List<Int>
) {
    companion object {
        fun randomTarget(): Int = (100..999).random()

        fun randomNumbers(): List<Int> {
            val singleDigit = (1..9).shuffled().take(4)
            val medium = listOf(10, 15, 20).random()
            val large = listOf(25, 50, 75, 100).random()
            return (singleDigit + medium + large).shuffled()
        }
    }
}

enum class Liga(val displayName: String, val emoji: String) {
    NULTA("Nulta liga", "🌱"),
    BRONZANA("Bronzana liga", "🥉"),
    SREBRNA("Srebrna liga", "🥈"),
    ZLATNA("Zlatna liga", "🥇"),
    PLATINASTA("Platinasta liga", "💎"),
    DIJAMANTSKA("Dijamantska liga", "💠");

    companion object {

        fun fromIndex(index: Int): Liga = values().getOrElse(index) { NULTA }
    }
}

data class UserProfile(
    val username: String,
    val email: String,
    val avatarResId: Int,
    val tokens: Int,
    val totalStars: Int,
    val league: Liga,
    val region: String,
    val qrPayload: String
)

data class GameStatistic(
    val gameName: String,
    val averagePointsLabel: String,
    val mainMetricLabel: String,
    val mainMetricPercent: Float,
    val gamesPlayed: Int
)

data class PlayerStats(
    val koZnaZna: GameStatistic,
    val mojBroj: GameStatistic,
    val korakPoKorak: GameStatistic,
    val asocijacije: GameStatistic,
    val skocko: GameStatistic,
    val spojnice: GameStatistic,
    val totalGamesPlayed: Int,
    val totalWins: Int,
    val totalLosses: Int
) {

    val winPercent: Float
        get() = if (totalGamesPlayed > 0) totalWins * 100f / totalGamesPlayed else 0f

    val lossPercent: Float
        get() = if (totalGamesPlayed > 0) totalLosses * 100f / totalGamesPlayed else 0f
}

data class KzzPitanje(
    val tekst: String,
    val odgovori: List<String>,
    val tacanIndex: Int
) {
    init {
        require(odgovori.size == 4) { "Pitanje mora imati tacno 4 odgovora" }
        require(tacanIndex in 0..3) { "tacanIndex mora biti u opsegu 0..3" }
    }
}

enum class KzzStanjePitanja {
    AKTIVNO,
    ODGOVORENO,
    ISTEKLO
}

data class KzzRezultat(
    val mojiBodovi: Int,
    val protivnikBodovi: Int,
    val mojiTacni: Int,
    val mojiNetacni: Int,
    val mojiPromaseni: Int
)

data class KzzOdgovor(val index: Int, val vremeMs: Long) {
    fun encode() = "$index,$vremeMs"

    companion object {
        const val NIJE_ODGOVORIO = -1

        fun decode(s: String): KzzOdgovor {
            val delovi = s.split(",")
            return KzzOdgovor(
                index = delovi.getOrNull(0)?.toIntOrNull() ?: NIJE_ODGOVORIO,
                vremeMs = delovi.getOrNull(1)?.toLongOrNull() ?: 0L
            )
        }
    }
}

object KzzKonstante {
    const val BROJ_PITANJA = 5
    const val VREME_PO_PITANJU_S = 5
    const val BODOVA_ZA_TACAN = 10
    const val BODOVA_ZA_NETACAN = -5
    const val MAX_BODOVA = BROJ_PITANJA * BODOVA_ZA_TACAN
    const val MIN_BODOVA = BROJ_PITANJA * BODOVA_ZA_NETACAN
}

data class SpojniceRundaPodaci(
    val kriterijum: String,
    val leviPojmovi: List<String>,
    val desniPojmovi: List<String>,
    val tacneVeze: Map<Int, Int>
) {
    init {
        require(leviPojmovi.size == 5) { "Spojnice runda treba 5 levih pojmova" }
        require(desniPojmovi.size == 5) { "Spojnice runda treba 5 desnih pojmova" }
        require(tacneVeze.size == 5) { "Spojnice runda treba 5 veza" }
    }
}

enum class SpojniceStanjeCelije {
    POCETNO,
    SELEKTOVANA,
    POVEZANA_MOJA_TACNO,
    POVEZANA_MOJA_NETACNO,
    POVEZANA_PROTIVNIKOVA
}

data class SpojniceRezultat(
    val mojiBodovi: Int,
    val protivnikBodovi: Int,
    val mojeVeze: Int,
    val protivnikoVeza: Int
)

object SpojniceKonstante {
    const val BROJ_RUNDI = 2
    const val VREME_PO_RUNDI_S = 30
    const val POJMOVA_PO_KOLONI = 5
    const val BODOVA_PO_VEZI = 2
    const val MAX_BODOVA = BROJ_RUNDI * POJMOVA_PO_KOLONI * BODOVA_PO_VEZI
}

enum class AsocijacijaCelijaStanje {
    ZAKLJUCANO,
    OTKRIVENO,
    POGODENO_MOJE,
    POGODENO_PROTIVNIK
}

data class AsocijacijeRundaPodaci(
    val polja: List<List<String>>,
    val resenjaKolona: List<String>,
    val finalnoResenje: String
) {
    init {
        require(polja.size == 4) { "Asocijacije moraju imati 4 kolone" }
        require(polja.all { it.size == 4 }) { "Svaka kolona mora imati 4 polja" }
        require(resenjaKolona.size == 4) { "Mora biti 4 resenja kolona" }
    }
}

data class AsocijacijeRezultat(
    val mojiBodovi: Int,
    val protivnikBodovi: Int,
    val mojeResenja: Int,
    val protivnikoveResenja: Int
)

object AsocijacijeKonstante {
    const val BROJ_RUNDI = 2
    const val VREME_PO_RUNDI_S = 120
    const val POLJA_PO_KOLONI = 4
    const val BROJ_KOLONA = 4

    const val FINALNO_BAZA = 7
    const val KOLONA_BAZA = 2
    const val BODOVI_PO_NEOTVORENOM = 1
    const val BODOVA_NEOTVORENA_KOLONA = 6
}

data class KorakPojam(
    val rijec: String,
    val tragovi: List<String>
)

object KorakKonstante {
    const val BROJ_RUNDI = 2
    const val MAX_KORAKA = 7
    const val VRIJEME_PO_KORAKU_S = 10
    const val BODOVA_PRVI_KORAK = 20
    const val ODBITAK_PO_KORAKU = 2
    const val KRADJA = 5
}

object MojBrojKonstante {
    const val BROJ_RUNDI = 2
    const val VRIJEME_S = 60
    const val BODOVA_TACAN = 10
    const val BODOVA_BLIZI = 5
}
