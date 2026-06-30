package com.example.slagalica.data

import com.example.slagalica.model.MatchRewardOutcome
import com.example.slagalica.model.ReconcileOutcome
import kotlinx.coroutines.tasks.await
import kotlin.math.max

/**
 * Napredovanje igrača poslije partije: zvezde, tokeni i liga (spec 3.d + 6).
 *
 * Glavna ulazna tačka je [applyMatchResult], koju treba pozvati JEDNOM po
 * završenoj partiji, sa stanovišta ulogovanog igrača. Svaki igrač upisuje
 * SVOJ rezultat na svom uređaju - tako se poštuju Firestore pravila
 * (users/{uid} mijenja samo vlasnik) i ne treba server.
 */
class ProgressionRepository {

    private val db = FirebaseProvider.db

    /**
     * Primjenjuje ishod partije na ulogovanog igrača i vraća [MatchRewardOutcome]
     * (za prikaz dijaloga/notifikacije o zvezdama, tokenima i promjeni lige).
     * Vraća null ako igrač nije ulogovan (neregistrovan igrač ne napreduje).
     *
     * @param won da li je igrač pobijedio partiju
     * @param totalPoints ukupno osvojeni bodovi u partiji (zbir svih igara)
     */
    suspend fun applyMatchResult(won: Boolean, totalPoints: Int): MatchRewardOutcome? {
        val uid = FirebaseProvider.currentUid ?: return null
        val ref = db.collection(FirestoreCollections.USERS).document(uid)

        return db.runTransaction { tx ->
            val snap = tx.get(ref)

            val stars = (snap.getLong("stars") ?: 0L).toInt()
            val tokens = (snap.getLong("tokens") ?: 0L).toInt()
            val starsWeekly = (snap.getLong("starsWeekly") ?: 0L).toInt()
            val starsMonthly = (snap.getLong("starsMonthly") ?: 0L).toInt()
            val lifetime = (snap.getLong("lifetimeStars") ?: 0L).toInt()
            val tokensFromStars = (snap.getLong("tokensFromStars") ?: 0L).toInt()
            val oldLeague = (snap.getLong("league") ?: 0L).toInt()

            val calc = LeagueManager.obracunZvezda(won, totalPoints)

            // Balans zvezda ne može u minus (spec: ko nema zvezde ne može ih izgubiti)
            val newStars = max(0, stars + calc.deltaStars)
            val newWeekly = max(0, starsWeekly + calc.deltaStars)
            val newMonthly = max(0, starsMonthly + calc.deltaStars)

            // Kumulativno osvojene zvezde i tokeni iz pragova od 50
            val newLifetime = lifetime + calc.earnedStars
            val tokensAwarded = LeagueManager.noviTokeni(lifetime, newLifetime)
            val newTokens = tokens + tokensAwarded
            val newTokensFromStars = tokensFromStars + tokensAwarded

            val newLeague = LeagueManager.ligaIndexZa(newStars)

            tx.update(ref, mapOf(
                "stars" to newStars,
                "tokens" to newTokens,
                "starsWeekly" to newWeekly,
                "starsMonthly" to newMonthly,
                "lifetimeStars" to newLifetime,
                "tokensFromStars" to newTokensFromStars,
                "league" to newLeague
            ))

            MatchRewardOutcome(
                deltaStars = newStars - stars,
                newStars = newStars,
                tokensAwarded = tokensAwarded,
                oldLeague = oldLeague,
                newLeague = newLeague
            )
        }.await()
    }

    /**
     * Lazy reconcile - obrada vremenskih događaja pri pokretanju aplikacije,
     * bez servera (spec 3.a dnevni tokeni, 6.b benefit lige, reset ciklusa zvezda).
     * Idempotentno: ako nije prošao dan ni ciklus, ništa ne mijenja.
     *
     * NAPOMENA: kazna 30% (6.e) i nagrade za plasman zavise od rang liste
     * (stavka 4, kolegina) - dodaju se kad ona bude postojala.
     */
    suspend fun reconcileOnStart(): ReconcileOutcome? {
        val uid = FirebaseProvider.currentUid ?: return null
        val ref = db.collection(FirestoreCollections.USERS).document(uid)

        return db.runTransaction { tx ->
            val snap = tx.get(ref)
            val tokens = (snap.getLong("tokens") ?: 0L).toInt()
            val league = (snap.getLong("league") ?: 0L).toInt()
            val lastDailyGrant = snap.getLong("lastDailyGrant") ?: 0L
            val lastWeekly = snap.getString("lastCycleWeekly") ?: ""
            val lastMonthly = snap.getString("lastCycleMonthly") ?: ""

            val updates = hashMapOf<String, Any>()

            // === Dnevni tokeni (5 + nivo lige, po danu) ===
            var tokensAdded = 0
            if (lastDailyGrant == 0L) {
                // Prvi put: ne dajemo retroaktivno (registracija je već dala 5), samo zapamtimo dan.
                updates["lastDailyGrant"] = System.currentTimeMillis()
            } else {
                val dana = Cycles.danaOd(lastDailyGrant)
                if (dana > 0) {
                    tokensAdded = (dana * LeagueManager.tokeniPoDanu(league)).toInt()
                    updates["tokens"] = tokens + tokensAdded
                    updates["lastDailyGrant"] = System.currentTimeMillis()
                }
            }

            // === Reset zvezda po ciklusu ===
            val weeklyId = Cycles.weekly()
            val monthlyId = Cycles.monthly()
            val weeklyReset = lastWeekly != weeklyId
            val monthlyReset = lastMonthly != monthlyId
            if (weeklyReset) {
                updates["starsWeekly"] = 0
                updates["lastCycleWeekly"] = weeklyId
            }
            if (monthlyReset) {
                updates["starsMonthly"] = 0
                updates["lastCycleMonthly"] = monthlyId
            }

            if (updates.isNotEmpty()) tx.update(ref, updates)

            // weeklyReset/monthlyReset su true i pri prvoj inicijalizaciji praznih polja -
            // za prikaz korisniku računa se samo dodjela tokena.
            ReconcileOutcome(tokensAdded, weeklyReset && lastWeekly.isNotEmpty(),
                monthlyReset && lastMonthly.isNotEmpty())
        }.await()
    }
}
