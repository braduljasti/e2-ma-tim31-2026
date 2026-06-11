package com.example.slagalica.data

import com.example.slagalica.model.SkockoSymbol
import kotlin.math.abs

/**
 * Čista logika igara - generisanje konfiguracije runde, provjera i bodovanje
 * po specifikaciji. Dijeli je i host (koji boduje runde) i klijent (za prikaz).
 * Sve radi nad ordinalima (Int) jer se tako lako čuva u Firestore-u.
 */
object GameLogic {

    private val rng = java.util.Random()

    // ===== SKOČKO =====

    /** Tajna kombinacija: 4 znaka od 6 (ordinali 0..5), ponavljanje dozvoljeno. */
    fun newSkockoSecret(): List<Int> =
        (0 until 4).map { rng.nextInt(SkockoSymbol.values().size) }

    /** Vraća (tačnih na poziciji, tačnih na pogrešnoj poziciji). */
    fun evaluateSkocko(secret: List<Int>, guess: List<Int>): Pair<Int, Int> {
        var correct = 0
        val secretLeft = mutableListOf<Int>()
        val guessLeft = mutableListOf<Int>()
        for (i in 0 until 4) {
            if (i < guess.size && guess[i] == secret[i]) correct++
            else { secretLeft.add(secret[i]); if (i < guess.size) guessLeft.add(guess[i]) }
        }
        var misplaced = 0
        for (g in guessLeft) if (secretLeft.remove(g)) misplaced++
        return correct to misplaced
    }

    /** Bodovi za pogodak u pokušaju: 1-2 →20, 3-4 →15, 5-6 →10. */
    fun skockoPointsForAttempt(attempt: Int): Int = when (attempt) {
        1, 2 -> 20; 3, 4 -> 15; else -> 10
    }

    const val SKOCKO_STEAL = 10

    /**
     * Bodovanje cijele Skočko runde iz pokušaja oba igrača.
     * starterGuesses/oppGuesses su liste pokušaja (svaki = 4 ordinala).
     * Vraća (bodovi startera, bodovi protivnika).
     */
    fun resolveSkocko(
        secret: List<Int>,
        starterGuesses: List<List<Int>>,
        oppGuesses: List<List<Int>>
    ): Pair<Int, Int> {
        fun solvedAt(guesses: List<List<Int>>): Int {
            for (i in guesses.indices) {
                if (i >= 6) break
                if (evaluateSkocko(secret, guesses[i]).first == 4) return i + 1
            }
            return 0
        }
        val sAttempt = solvedAt(starterGuesses)
        if (sAttempt > 0) return skockoPointsForAttempt(sAttempt) to 0
        if (solvedAt(oppGuesses) > 0) return 0 to SKOCKO_STEAL   // d) krađa 10
        return 0 to 0
    }

    // ===== KORAK PO KORAK (za proširenje na multiplayer) =====

    data class KorakConfig(val word: String, val hints: List<String>)

    private val korakBank = listOf(
        KorakConfig("TESLA", listOf(
            "Rođen 1856. godine", "Srpskog porijekla", "Radio u kompaniji Edison",
            "Izmislio izmjeničnu struju", "Naučnik i izumitelj", "Ime mu je Nikola",
            "Tesla Motors nosi njegovo ime")),
        KorakConfig("BEOGRAD", listOf(
            "Na ušću dvije rijeke", "Sadrži čuvenu tvrđavu", "Kalemegdan",
            "Sava i Dunav", "Preko 1,5 miliona stanovnika", "U centralnoj Srbiji",
            "Glavni grad Srbije"))
    )

    fun newKorakConfig(): KorakConfig = korakBank[rng.nextInt(korakBank.size)]
    fun korakPointsForStep(step: Int): Int = maxOf(0, 20 - (step - 1) * 2)
    const val KORAK_STEAL = 5
    fun korakCorrect(target: String, guess: String) = target.trim().equals(guess.trim(), ignoreCase = true)

    fun resolveKorak(target: String, sGuess: String, sStep: Int, oGuess: String): Pair<Int, Int> {
        if (korakCorrect(target, sGuess)) return korakPointsForStep(sStep.coerceIn(1, 7)) to 0
        if (korakCorrect(target, oGuess)) return 0 to KORAK_STEAL
        return 0 to 0
    }

    // ===== MOJ BROJ (za proširenje na multiplayer) =====

    fun newMojBrojTarget(): Int = 100 + rng.nextInt(900)
    fun newMojBrojNumbers(): List<Int> {
        val single = (0 until 4).map { 1 + rng.nextInt(9) }
        val medium = listOf(10, 15, 20)[rng.nextInt(3)]
        val large = listOf(25, 50, 75, 100)[rng.nextInt(4)]
        return (single + medium + large).shuffled()
    }

    const val MOJBROJ_EXACT = 10
    const val MOJBROJ_CLOSER = 5

    /** (validan, vrijednost, tačan) - provjerava i da su korišteni samo dozvoljeni brojevi. */
    fun evalMojBroj(expr: String, target: Int, available: List<Int>): Triple<Boolean, Int, Boolean> {
        if (expr.isBlank()) return Triple(false, 0, false)
        val used = Regex("\\d+").findAll(expr).map { it.value.toInt() }.toList()
        val pool = available.toMutableList()
        for (n in used) if (!pool.remove(n)) return Triple(false, 0, false)
        return try {
            val v = SafeMath.eval(expr.replace("×", "*").replace("÷", "/"))
            if (v != Math.floor(v)) Triple(false, 0, false)
            else Triple(true, v.toInt(), v.toInt() == target)
        } catch (e: Exception) { Triple(false, 0, false) }
    }

    fun resolveMojBroj(target: Int, available: List<Int>, sExpr: String, oExpr: String): Pair<Int, Int> {
        val s = evalMojBroj(sExpr, target, available)
        val o = evalMojBroj(oExpr, target, available)
        if (s.third) return MOJBROJ_EXACT to 0
        if (o.third) return 0 to MOJBROJ_EXACT
        val sDiff = if (s.first) abs(s.second - target) else Int.MAX_VALUE
        val oDiff = if (o.first) abs(o.second - target) else Int.MAX_VALUE
        return when {
            sDiff == Int.MAX_VALUE && oDiff == Int.MAX_VALUE -> 0 to 0
            sDiff < oDiff -> MOJBROJ_CLOSER to 0
            oDiff < sDiff -> 0 to MOJBROJ_CLOSER
            else -> MOJBROJ_CLOSER to 0   // jednaka razlika -> čija je runda
        }
    }
}

/** Mali siguran evaluator izraza (bez eval-a). */
object SafeMath {
    fun eval(expr: String): Double {
        return object {
            var pos = -1; var ch = ' '
            fun next() { ch = if (++pos < expr.length) expr[pos] else '\u0000' }
            fun eat(c: Char): Boolean { while (ch == ' ') next(); return if (ch == c) { next(); true } else false }
            fun parse(): Double { next(); val x = expr(); if (pos < expr.length) throw RuntimeException(); return x }
            fun expr(): Double { var x = term(); while (true) x = when { eat('+') -> x + term(); eat('-') -> x - term(); else -> return x } }
            fun term(): Double { var x = factor(); while (true) x = when { eat('*') -> x * factor(); eat('/') -> x / factor(); else -> return x } }
            fun factor(): Double {
                if (eat('+')) return factor(); if (eat('-')) return -factor()
                val start = pos
                return if (eat('(')) { val x = expr(); eat(')'); x }
                else if (ch in '0'..'9' || ch == '.') { while (ch in '0'..'9' || ch == '.') next(); expr.substring(start, pos).toDouble() }
                else throw RuntimeException()
            }
        }.parse()
    }
}
