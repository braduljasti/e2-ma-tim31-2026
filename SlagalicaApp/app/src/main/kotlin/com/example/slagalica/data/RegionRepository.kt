package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.example.slagalica.model.IgracTacka
import com.example.slagalica.model.RegionRangRed
import com.example.slagalica.model.RegionStatistika
import kotlinx.coroutines.tasks.await

/**
 * Podaci za prikaz regiona (spec 5.a, 5.b): tačke svih igrača na mapi i
 * mjesečna rang lista po regionima. Sve se računa na klijentu iz kolekcije
 * `users` - bez servera (za projekat sa malim brojem igrača sasvim dovoljno).
 */
class RegionRepository {

    private val db = FirebaseProvider.db
    private val standingsRepo = RegionStandingsRepository()

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

    /** Statistika jednog regiona na klik (spec 5.d): registrovani, aktivni, broj mjesta. */
    suspend fun statistikaRegiona(naziv: String): RegionStatistika {
        val info = Regioni.zaNaziv(naziv)
        val sada = System.currentTimeMillis()
        val uRegionu = sviKorisnici().filter { it.region == naziv }
        val aktivni = uRegionu.count { sada - it.lastSeen < PresenceRepository.AKTIVAN_PRAG_MS }
        val mjesta = runCatching { standingsRepo.brojMjestaPoRegionu()[naziv] }.getOrNull()
        return RegionStatistika(
            naziv = naziv,
            emoji = info?.emoji ?: "🌍",
            registrovani = uRegionu.size,
            aktivni = aktivni,
            prvaMjesta = mjesta?.first ?: 0,
            drugaMjesta = mjesta?.second ?: 0,
            trecaMjesta = mjesta?.third ?: 0
        )
    }

    /** Trenutni poredak regiona (po zvezdama ciklusa) - za arhiviranje ciklusa. */
    private suspend fun trenutniPoredak(): List<String> =
        rangPoRegionima().map { it.regionNaziv }

    /**
     * Lijeno arhiviranje: ako plasman prošlog mjeseca još nije snimljen, snimi
     * trenutni poredak pod prošli ciklus (idempotentno). Zove se pri pokretanju.
     */
    suspend fun arhivirajProsliCiklusAkoTreba() {
        val prosli = Cycles.prethodniMjesec()
        standingsRepo.arhivirajAkoTreba(prosli, trenutniPoredak())
    }
}
