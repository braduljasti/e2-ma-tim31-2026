package com.example.slagalica.data

import com.example.slagalica.model.MatchRewardOutcome
import com.example.slagalica.model.ReconcileOutcome
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class ProgressionRepository {

    private val db = FirebaseProvider.db

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

            val newStars = max(0, stars + calc.deltaStars)
            val newWeekly = max(0, starsWeekly + calc.deltaStars)
            val newMonthly = max(0, starsMonthly + calc.deltaStars)

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

            var tokensAdded = 0
            if (lastDailyGrant == 0L) {
                updates["lastDailyGrant"] = System.currentTimeMillis()
            } else {
                val dana = Cycles.danaOd(lastDailyGrant)
                if (dana > 0) {
                    tokensAdded = (dana * LeagueManager.tokeniPoDanu(league)).toInt()
                    updates["tokens"] = tokens + tokensAdded
                    updates["lastDailyGrant"] = System.currentTimeMillis()
                }
            }

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

            ReconcileOutcome(tokensAdded, weeklyReset && lastWeekly.isNotEmpty(),
                monthlyReset && lastMonthly.isNotEmpty())
        }.await()
    }
}
