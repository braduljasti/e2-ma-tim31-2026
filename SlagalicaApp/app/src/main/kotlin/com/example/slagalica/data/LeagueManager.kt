package com.example.slagalica.data

/**
 * Čista logika napredovanja kroz lige i obračuna zvezda/tokena (spec 3.d + 6).
 * Bez Firebase-a i bez Androida - lako za jedinično testiranje i odbranu.
 * Pozива je [ProgressionRepository] unutar transakcije.
 */
object LeagueManager {

    /**
     * Pragovi zvezda za ulazak u ligu (spec 6.c): prva liga 100, svaka naredna ×2.
     * Indeks lige = koliko je pragova dostignuto. 0 zvezda → liga 0 (nulta);
     * 100 → liga 1; 250 → liga 2; … 1600+ → liga 5 (nulta + 5 liga).
     */
    val PRAGOVI = listOf(100, 200, 400, 800, 1600)

    fun ligaIndexZa(stars: Int): Int = PRAGOVI.count { stars >= it }

    /** Ukupan broj zvezda potreban za ulazak u datu ligu (liga 0 = 0). */
    fun pragLige(ligaIndex: Int): Int = if (ligaIndex <= 0) 0 else PRAGOVI.getOrElse(ligaIndex - 1) { PRAGOVI.last() }

    // ===== Benefiti lige (spec 6.b) =====

    const val DNEVNI_TOKENI_BAZA = 5     // 5 tokena svaki dan (spec 3.a)

    /** Tokeni koje igrač dobija svaki dan: baza + 1 po nivou lige (spec 6.b). */
    fun tokeniPoDanu(ligaIndex: Int): Int = DNEVNI_TOKENI_BAZA + ligaIndex

    // ===== Bodovanje zvezda po partiji (spec 3.d) =====

    const val STARS_WIN = 10
    const val STARS_LOSS = -10
    const val POINTS_PER_STAR = 40   // +1 zvezda za svakih 40 osvojenih bodova
    const val STARS_PER_TOKEN = 50   // 50 osvojenih zvezda → 1 token (3.d.iii)

    /**
     * `deltaStars` = promjena balansa (pobjednik +10, gubitnik -10, oba +bodovi/40).
     * `earnedStars` = SAMO pozitivno osvojeno (za pragove tokena) - gubitak od 10
     *  se ne računa kao "osvojeno", pa ne smanjuje lifetime brojač.
     */
    data class RewardCalc(val deltaStars: Int, val earnedStars: Int)

    fun obracunZvezda(won: Boolean, points: Int): RewardCalc {
        val bonus = points / POINTS_PER_STAR            // npr. 150 bodova → 3
        return if (won) {
            RewardCalc(deltaStars = STARS_WIN + bonus, earnedStars = STARS_WIN + bonus)
        } else {
            RewardCalc(deltaStars = STARS_LOSS + bonus, earnedStars = bonus)
        }
    }

    /**
     * Koliko NOVIH tokena slijeduje kad lifetimeStars poraste sa [staroLifetime]
     * na [novoLifetime]. Token se dodjeljuje za svaki pređeni prag od 50.
     */
    fun noviTokeni(staroLifetime: Int, novoLifetime: Int): Int =
        (novoLifetime / STARS_PER_TOKEN) - (staroLifetime / STARS_PER_TOKEN)
}
