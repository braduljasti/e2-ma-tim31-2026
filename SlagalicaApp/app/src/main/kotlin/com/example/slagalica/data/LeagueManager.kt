package com.example.slagalica.data

object LeagueManager {

    val PRAGOVI = listOf(100, 200, 400, 800, 1600)

    fun ligaIndexZa(stars: Int): Int = PRAGOVI.count { stars >= it }

    fun pragLige(ligaIndex: Int): Int = if (ligaIndex <= 0) 0 else PRAGOVI.getOrElse(ligaIndex - 1) { PRAGOVI.last() }

    const val DNEVNI_TOKENI_BAZA = 5

    fun tokeniPoDanu(ligaIndex: Int): Int = DNEVNI_TOKENI_BAZA + ligaIndex

    const val STARS_WIN = 10
    const val STARS_LOSS = -10
    const val POINTS_PER_STAR = 40
    const val STARS_PER_TOKEN = 50

    data class RewardCalc(val deltaStars: Int, val earnedStars: Int)

    fun obracunZvezda(won: Boolean, points: Int): RewardCalc {
        val bonus = points / POINTS_PER_STAR
        return if (won) {
            RewardCalc(deltaStars = STARS_WIN + bonus, earnedStars = STARS_WIN + bonus)
        } else {
            RewardCalc(deltaStars = STARS_LOSS + bonus, earnedStars = bonus)
        }
    }

    fun noviTokeni(staroLifetime: Int, novoLifetime: Int): Int =
        (novoLifetime / STARS_PER_TOKEN) - (staroLifetime / STARS_PER_TOKEN)
}
