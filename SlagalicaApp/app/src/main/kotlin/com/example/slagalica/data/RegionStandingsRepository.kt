package com.example.slagalica.data

import kotlinx.coroutines.tasks.await

/**
 * Istorija plasmana regiona po ciklusu (spec 5.d "broj 1./2./3. mjesta", 5.e okviri).
 * Dokument `regionStandings/{cycleId}` → { poredak: [regioni od 1. mjesta nadolje] }.
 *
 * Bez servera: arhiviranje je lijeno - prvi klijent koji uđe u novi ciklus snimi
 * poredak prošlog (idempotentno, ne prepisuje). Za pouzdan test poredak se može
 * napuniti i seed skriptom.
 */
class RegionStandingsRepository {

    private val db = FirebaseProvider.db
    private val col get() = db.collection(FirestoreCollections.REGION_STANDINGS)

    /** Snima poredak za dati ciklus samo ako još ne postoji (ne prepisuje istoriju). */
    suspend fun arhivirajAkoTreba(cycleId: String, poredak: List<String>) {
        if (poredak.isEmpty()) return
        val ref = col.document(cycleId)
        if (ref.get().await().exists()) return
        ref.set(mapOf("poredak" to poredak)).await()
    }

    /** Poredak regiona za dati ciklus (null ako nije arhiviran). */
    suspend fun poredakZa(cycleId: String): List<String>? =
        (col.document(cycleId).get().await().get("poredak") as? List<*>)
            ?.mapNotNull { it as? String }

    /** Broj 1./2./3. mjesta po regionu kroz cijelu istoriju (spec 5.d). */
    suspend fun brojMjestaPoRegionu(): Map<String, Triple<Int, Int, Int>> {
        val rezultat = HashMap<String, IntArray>()
        col.get().await().documents.forEach { doc ->
            val poredak = (doc.get("poredak") as? List<*>)?.mapNotNull { it as? String } ?: return@forEach
            poredak.take(3).forEachIndexed { mjesto, region ->
                val br = rezultat.getOrPut(region) { IntArray(3) }
                br[mjesto]++
            }
        }
        return rezultat.mapValues { (_, a) -> Triple(a[0], a[1], a[2]) }
    }
}
