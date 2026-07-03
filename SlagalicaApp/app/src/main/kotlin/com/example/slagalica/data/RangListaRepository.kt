package com.example.slagalica.data

import com.example.slagalica.model.AppNotification
import com.example.slagalica.model.NotificationCategory
import com.example.slagalica.model.RangCiklus
import com.example.slagalica.model.RangListaStavka
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor

/**
 * Rang lista (spec 4) - nedeljna i mesečna, izvedena DIREKTNO iz users.starsWeekly / starsMonthly,
 * koje već održava [ProgressionRepository] (applyMatchResult + reconcileOnStart - kolegin dio,
 * spec 3.d/6.b). Nema posebne kolekcije za rang listu - to znači i manje posla oko sinhronizacije
 * i da je lista uvijek "uživo" tačna.
 *
 * Ova klasa dodaje ono što je u HANDOFF_STUDENT1.md ostavljeno meni:
 *  - dodjelu tokena za plasman na kraju ciklusa (spec 4.c),
 *  - kaznu od 30% zvezda za igrače koji se nisu plasirali na mjesečnoj rang listi (spec 6.e).
 *
 * VAŽNO: pošto projekat nema server, [pripremiZavrsetakCiklusaAkoTreba] MORA da se pozove PRIJE
 * [ProgressionRepository.reconcileOnStart] pri pokretanju aplikacije - inače bi reconcile već
 * resetovao brojače na 0 prije nego što stignemo da vidimo plasman prošlog ciklusa. Čak i tako,
 * pošto svaki korisnik nezavisno (lijeno) detektuje kraj ciklusa kad prvi put otvori aplikaciju,
 * plasman je "najbolja moguća procjena u tom trenutku", a ne savršeno tačan snapshot - isti
 * kompromis koji kolega već pravi u RegionRepository.arhivirajProsliCiklusAkoTreba().
 */
class RangListaRepository {

    private val db = FirebaseProvider.db
    private fun users() = db.collection(FirestoreCollections.USERS)

    companion object {
        private val DAY_FMT = SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault())

        /** Opseg datuma tekućeg ciklusa, za prikaz u UI-ju (spec 4.e). */
        fun cycleDateRange(ciklus: RangCiklus, date: Date = Date()): String {
            val cal = Calendar.getInstance(Locale.getDefault())
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.time = date
            val start: Date
            val end: Date
            if (ciklus == RangCiklus.NEDELJNI) {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                start = cal.time
                cal.add(Calendar.DAY_OF_WEEK, 6)
                end = cal.time
            } else {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                start = cal.time
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                end = cal.time
            }
            return "${DAY_FMT.format(start)} - ${DAY_FMT.format(end)}"
        }

        /** Tokeni koje nosi dato mjesto na rang listi, po specifikaciji (4.c). */
        fun tokenNagrada(ciklus: RangCiklus, mesto: Int): Int = when {
            mesto == 1 -> if (ciklus == RangCiklus.NEDELJNI) 5 else 10
            mesto == 2 -> if (ciklus == RangCiklus.NEDELJNI) 3 else 6
            mesto == 3 -> if (ciklus == RangCiklus.NEDELJNI) 2 else 4
            mesto in 4..10 -> if (ciklus == RangCiklus.NEDELJNI) 1 else 2
            else -> 0
        }
    }

    /** Top N igrača tekućeg ciklusa (za ekran Rang liste). Samo igrači sa >0 zvezda u ciklusu. */
    suspend fun ucitajRangListu(ciklus: RangCiklus, limit: Long = 50): List<RangListaStavka> {
        val polje = if (ciklus == RangCiklus.NEDELJNI) "starsWeekly" else "starsMonthly"
        return runCatching {
            users()
                .whereGreaterThan(polje, 0)
                .orderBy(polje, Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
                .documents.map { d ->
                    RangListaStavka(
                        uid = d.id,
                        username = d.getString("username") ?: "Igrač",
                        league = (d.getLong("league") ?: 0L).toInt(),
                        stars = (d.getLong(polje) ?: 0L).toInt()
                    )
                }
        }.getOrDefault(emptyList())
    }

    /**
     * Poziva se PRIJE [ProgressionRepository.reconcileOnStart] pri pokretanju aplikacije (vidi
     * napomenu u dokumentaciji klase). Idempotentno - bezbjedno je pozivati ga pri svakom
     * pokretanju (čuva se u users.rewardedCycles).
     */
    suspend fun pripremiZavrsetakCiklusaAkoTreba(uid: String): List<com.example.slagalica.model.NagradaCiklusa> {
        val ref = users().document(uid)
        val snap = runCatching { ref.get().await() }.getOrNull() ?: return emptyList()
        val vecObradjeno = (snap.get("rewardedCycles") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        return listOfNotNull(
            obradiCiklus(ref, snap, RangCiklus.NEDELJNI, vecObradjeno),
            obradiCiklus(ref, snap, RangCiklus.MESECNI, vecObradjeno)
        )
    }

    private suspend fun obradiCiklus(
        ref: DocumentReference,
        snap: DocumentSnapshot,
        ciklus: RangCiklus,
        vecObradjeno: List<String>
    ): com.example.slagalica.model.NagradaCiklusa? {
        val poljeStars = if (ciklus == RangCiklus.NEDELJNI) "starsWeekly" else "starsMonthly"
        val poljeCiklus = if (ciklus == RangCiklus.NEDELJNI) "lastCycleWeekly" else "lastCycleMonthly"
        val prefiks = if (ciklus == RangCiklus.NEDELJNI) "W-" else "M-"

        val zadnjiVidjeni = snap.getString(poljeCiklus) ?: ""
        val trenutniCiklus = if (ciklus == RangCiklus.NEDELJNI) Cycles.weekly() else Cycles.monthly()
        // Prazno = korisnik nikad nije prošao kroz reconcile (novoregistrovan) - ništa za obraditi.
        // Isti ključ = ciklus se još nije promijenio otkad je zadnji put viđen - takođe ništa.
        if (zadnjiVidjeni.isBlank() || zadnjiVidjeni == trenutniCiklus) return null

        val rewardId = prefiks + zadnjiVidjeni
        if (rewardId in vecObradjeno) return null

        val mojeZvezdeCiklusa = (snap.getLong(poljeStars) ?: 0L).toInt()
        val poredak = runCatching { ucitajRangListu(ciklus, limit = 10) }.getOrDefault(emptyList())
        val mesto = poredak.indexOfFirst { it.uid == ref.id }

        val updates = hashMapOf<String, Any>("rewardedCycles" to FieldValue.arrayUnion(rewardId))
        val notifRepo = NotifikacijeRepository()
        var rezultat: com.example.slagalica.model.NagradaCiklusa? = null

        if (mesto >= 0) {
            // Spec 4.c: nagrada za plasman u top 10
            val tokeni = tokenNagrada(ciklus, mesto + 1)
            if (tokeni > 0) {
                updates["tokens"] = FieldValue.increment(tokeni.toLong())
                val naziv = if (ciklus == RangCiklus.NEDELJNI) "nedeljnoj" else "mesečnoj"
                rezultat = com.example.slagalica.model.NagradaCiklusa(
                    ciklus = ciklus, mesto = mesto + 1, tokeni = tokeni, kaznjen = false
                )
                runCatching {
                    notifRepo.add(
                        AppNotification(
                            id = "",
                            title = "🏆 Osvojili ste nagradu!",
                            content = "Zauzeli ste ${mesto + 1}. mjesto na $naziv rang listi i osvojili $tokeni token(a)!",
                            category = NotificationCategory.REWARDS,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        } else if (ciklus == RangCiklus.MESECNI && mojeZvezdeCiklusa <= 0) {
            // Spec 6.e: nije se plasirao na mjesečnoj rang listi (nije odigrao nijednu partiju
            // ili je završio sa 0 osvojenih zvezda tog mjeseca) -> gubi 30% ukupnih zvezda.
            val trenutneZvezde = (snap.getLong("stars") ?: 0L).toInt()
            if (trenutneZvezde > 0) {
                val noveZvezde = floor(trenutneZvezde * 0.7).toInt()
                updates["stars"] = noveZvezde
                updates["league"] = LeagueManager.ligaIndexZa(noveZvezde)
                runCatching {
                    notifRepo.add(
                        AppNotification(
                            id = "",
                            title = "📉 Niste bili rangirani",
                            content = "Prošlog mjeseca niste odigrali nijednu partiju, pa ste izgubili 30% zvezda.",
                            category = NotificationCategory.REWARDS,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        runCatching { ref.update(updates).await() }
        return rezultat
    }
}
