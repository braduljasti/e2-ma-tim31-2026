package com.example.slagalica.model

enum class NotificationCategory {
    CHAT, RANK, REWARDS, OTHER
}

data class AppNotification(
    val id: Long,
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


// ===== PROFIL =====

/**
 * Liga u kojoj se korisnik trenutno nalazi.
 * Po specifikaciji 1.f, treba prikazati naziv i ikonu lige.
 * Emoji koristimo u tekstu, a `iconResId` u ImageView-u.
 */
enum class Liga(val displayName: String, val emoji: String) {
    BRONZANA("Bronzana liga", "🥉"),
    SREBRNA("Srebrna liga", "🥈"),
    ZLATNA("Zlatna liga", "🥇"),
    PLATINASTA("Platinasta liga", "💎"),
    DIJAMANTSKA("Dijamantska liga", "💠")
}

/**
 * Osnovni podaci o korisniku koji se prikazuju u zaglavlju profila.
 * `qrPayload` je tekst koji se kodira u QR — najčešće je to neki ID
 * koji bi backend prepoznao kao "poziv prijatelja", ali pošto nema
 * backenda, hardkodujemo nešto poput "slagalica://invite/USERNAME".
 */
data class UserProfile(
    val username: String,
    val email: String,
    val avatarResId: Int,   // R.drawable.avatar_default itd.
    val tokens: Int,
    val totalStars: Int,
    val league: Liga,
    val region: String,
    val qrPayload: String
)

/**
 * Statistika za pojedinačnu igru.
 * - `averagePointsLabel`: tekstualni opseg, npr. "40 - 60 bodova"
 *   (po specifikaciji 1.c.i je "opseg prosečno osvojenih bodova")
 * - `mainMetricLabel` / `mainMetricPercent`: glavna metrika specifična
 *   za igru, npr. za "Ko zna zna" -> "Pogođenih pitanja", procenat 70%.
 * - `gamesPlayed`: broj odigranih partija ove igre.
 */
data class GameStatistic(
    val gameName: String,
    val averagePointsLabel: String,
    val mainMetricLabel: String,
    val mainMetricPercent: Float,   // 0f..100f
    val gamesPlayed: Int
)

/**
 * Sva statistika igrača na jednom mestu.
 * Drži po jedan GameStatistic za svaku igru + ukupne brojke.
 */
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
    /** Specifikacija 1.c.ix - procenat pobeđenih partija */
    val winPercent: Float
        get() = if (totalGamesPlayed > 0) totalWins * 100f / totalGamesPlayed else 0f

    /** Specifikacija 1.c.ix - procenat izgubljenih partija */
    val lossPercent: Float
        get() = if (totalGamesPlayed > 0) totalLosses * 100f / totalGamesPlayed else 0f
}


// ===== KO ZNA ZNA =====

/**
 * Jedno pitanje u igri "Ko zna zna".
 * Po specifikaciji: tacno 4 ponudjena odgovora, jedan je tacan.
 */
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

/**
 * Stanje aktuelnog pitanja - kontroliše vizuelnu povratnu informaciju
 * (boju dugmadi i da li su klikabilna).
 */
enum class KzzStanjePitanja {
    AKTIVNO,        // tajmer ide, korisnik bira
    ODGOVORENO,     // korisnik je kliknuo - prikazi zelenu/crvenu boju
    ISTEKLO         // istekao timer bez odgovora - prikazi tacan u zelenoj
}

/**
 * Snapshot rezultata jedne odigrane runde - koristi se za finalni dijalog.
 */
data class KzzRezultat(
    val mojiBodovi: Int,
    val protivnikBodovi: Int,
    val mojiTacni: Int,
    val mojiNetacni: Int,
    val mojiPromaseni: Int   // istekao timer
)

/**
 * Konstante za igru - stavljam ih u object da budu lako dostupne
 * iz ViewModel-a, a ne razbacane po kodu.
 */
object KzzKonstante {
    const val BROJ_PITANJA = 5
    const val VREME_PO_PITANJU_S = 5
    const val BODOVA_ZA_TACAN = 10
    const val BODOVA_ZA_NETACAN = -5
    const val MAX_BODOVA = BROJ_PITANJA * BODOVA_ZA_TACAN          // 50
    const val MIN_BODOVA = BROJ_PITANJA * BODOVA_ZA_NETACAN        // -25
}

// ===== SPOJNICE =====

/**
 * Jedna runda u igri Spojnice.
 * Po specifikaciji: 5 pojmova levo, 5 desno, jedan kriterijum.
 *
 * `tacneVeze` mapira indeks levog pojma na indeks desnog koji mu odgovara.
 * Npr. tacneVeze[0] == 3 znaci da levi[0] ide sa desni[3].
 */
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

/**
 * Stanje pojedinacne celije (jedna kartica pojma).
 * Boja kartice u Fragmentu se odredjuje na osnovu ovog stanja.
 */
enum class SpojniceStanjeCelije {
    POCETNO,
    SELEKTOVANA,                // levi pojam koji je trenutno u fokusu
    POVEZANA_MOJA_TACNO,        // korisnik je pogodio - zeleno, trajno
    POVEZANA_MOJA_NETACNO,      // korisnik je pogresno spojio - crveno, trajno
    POVEZANA_PROTIVNIKOVA       // protivnik je spojio (sim) - plavo, trajno
}

/**
 * Snapshot finalnog rezultata - prikazuje se u finalnom dijalogu.
 */
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
    const val MAX_BODOVA = BROJ_RUNDI * POJMOVA_PO_KOLONI * BODOVA_PO_VEZI  // 20
}