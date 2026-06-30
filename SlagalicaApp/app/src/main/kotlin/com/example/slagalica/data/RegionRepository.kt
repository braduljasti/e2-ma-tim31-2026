package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.example.slagalica.model.IgracTacka
import com.example.slagalica.model.RegionRangRed
import kotlinx.coroutines.tasks.await

/**
 * Podaci za prikaz regiona (spec 5.a, 5.b): tačke svih igrača na mapi i
 * mjesečna rang lista po regionima. Sve se računa na klijentu iz kolekcije
 * `users` - bez servera (za projekat sa malim brojem igrača sasvim dovoljno).
 */
class RegionRepository {

    private val db = FirebaseProvider.db

    /** Učitava sve korisnike jednom; iz njih se izvode i tačke i rang lista. */
    private suspend fun sviKorisnici(): List<FirebaseUser> =
        db.collection(FirestoreCollections.USERS).get().await()
            .documents.mapNotNull { it.toObject(FirebaseUser::class.java) }

    /** Tačke svih igrača koji imaju validan region (pozicija je deterministička iz uid-a). */
    suspend fun tackeIgraca(): List<IgracTacka> {
        val me = FirebaseProvider.currentUid
        return sviKorisnici().mapNotNull { u ->
            val region = Regioni.zaNaziv(u.region) ?: return@mapNotNull null
            val (lat, lng) = Regioni.tackaZa(u.uid, region)
            IgracTacka(
                username = u.username,
                regionNaziv = u.region,
                lat = lat, lng = lng,
                jaSam = u.uid == me
            )
        }
    }

    /**
     * Mjesečna rang lista po regionima (spec 5.b): zbir `starsMonthly` svih
     * igrača regiona, sortirano opadajuće. Označava region ulogovanog igrača.
     */
    suspend fun rangPoRegionima(): List<RegionRangRed> {
        val korisnici = sviKorisnici()
        val mojRegion = korisnici.firstOrNull { it.uid == FirebaseProvider.currentUid }?.region

        return Regioni.SVI.map { info ->
            val uRegionu = korisnici.filter { it.region == info.naziv }
            RegionRangRed(
                regionNaziv = info.naziv,
                emoji = info.emoji,
                ukupnoZvezda = uRegionu.sumOf { it.starsMonthly },
                brojIgraca = uRegionu.size,
                mojRegion = info.naziv == mojRegion
            )
        }.sortedByDescending { it.ukupnoZvezda }
    }
}
