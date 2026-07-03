package com.example.slagalica.data

import kotlinx.coroutines.tasks.await

class RegionStandingsRepository {

    private val db = FirebaseProvider.db
    private val col get() = db.collection(FirestoreCollections.REGION_STANDINGS)

    suspend fun arhivirajAkoTreba(cycleId: String, poredak: List<String>) {
        if (poredak.isEmpty()) return
        val ref = col.document(cycleId)
        if (ref.get().await().exists()) return
        ref.set(mapOf("poredak" to poredak)).await()
    }

    suspend fun poredakZa(cycleId: String): List<String>? =
        (col.document(cycleId).get().await().get("poredak") as? List<*>)
            ?.mapNotNull { it as? String }

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
