package com.example.slagalica.data

import com.example.slagalica.model.AsocijacijeRundaPodaci
import com.example.slagalica.model.KzzPitanje
import com.example.slagalica.model.SpojniceRundaPodaci
import kotlinx.coroutines.tasks.await

/**
 * Podaci za igre Ko zna zna, Spojnice i Asocijacije iz Firestore baze.
 *
 * Struktura kolekcija:
 *   kzzQuestions/{id}      -> { tekst, odgovori: [4 stringa], tacanIndex }
 *   spojniceRunde/{id}     -> { kriterijum, levi: [5], desni: [5], veze: {"0": 1, ...} }
 *   asocijacijeRunde/{id}  -> { kolone: {A:[4], B:[4], C:[4], D:[4]}, resenjaKolona: [4], finalnoResenje }
 *
 * Napomena: Firestore ne podržava niz nizova, pa se 4x4 polja asocijacija
 * čuvaju kao mapa kolona (A-D), a veze spojnica kao mapa sa string ključevima.
 *
 * Podaci se inicijalno unose kroz seedIfEmpty() (poziva se pri pokretanju
 * aplikacije) - po specifikaciji je dovoljno par primera po igri.
 */
class GameDataRepository {

    private val db = FirebaseProvider.db

    // ============================================================
    // ČITANJE - nasumičan izbor za jednu partiju
    // ============================================================

    /** Vraća [broj] nasumičnih pitanja za Ko zna zna. */
    suspend fun nasumicnaKzzPitanja(broj: Int): List<KzzPitanje> {
        val sva = db.collection(FirestoreCollections.KZZ_QUESTIONS).get().await()
            .documents.mapNotNull { d ->
                runCatching {
                    KzzPitanje(
                        tekst = d.getString("tekst")!!,
                        odgovori = (d.get("odgovori") as List<*>).map { it as String },
                        tacanIndex = (d.getLong("tacanIndex"))!!.toInt()
                    )
                }.getOrNull()
            }
        require(sva.size >= broj) { "U bazi nema dovoljno KZZ pitanja (${sva.size}, treba $broj)" }
        return sva.shuffled().take(broj)
    }

    /** Vraća [broj] nasumičnih rundi za Spojnice. */
    suspend fun nasumicneSpojnice(broj: Int): List<SpojniceRundaPodaci> {
        val sve = db.collection(FirestoreCollections.SPOJNICE_RUNDE).get().await()
            .documents.mapNotNull { d ->
                runCatching {
                    SpojniceRundaPodaci(
                        kriterijum = d.getString("kriterijum")!!,
                        leviPojmovi = (d.get("levi") as List<*>).map { it as String },
                        desniPojmovi = (d.get("desni") as List<*>).map { it as String },
                        tacneVeze = (d.get("veze") as Map<*, *>).entries.associate {
                            (it.key as String).toInt() to (it.value as Number).toInt()
                        }
                    )
                }.getOrNull()
            }
        require(sve.size >= broj) { "U bazi nema dovoljno Spojnice rundi (${sve.size}, treba $broj)" }
        return sve.shuffled().take(broj)
    }

    /** Vraća [broj] nasumičnih rundi za Asocijacije. */
    suspend fun nasumicneAsocijacije(broj: Int): List<AsocijacijeRundaPodaci> {
        val sve = db.collection(FirestoreCollections.ASOCIJACIJE_RUNDE).get().await()
            .documents.mapNotNull { d ->
                runCatching {
                    val kolone = d.get("kolone") as Map<*, *>
                    AsocijacijeRundaPodaci(
                        polja = listOf("A", "B", "C", "D").map { k ->
                            (kolone[k] as List<*>).map { it as String }
                        },
                        resenjaKolona = (d.get("resenjaKolona") as List<*>).map { it as String },
                        finalnoResenje = d.getString("finalnoResenje")!!
                    )
                }.getOrNull()
            }
        require(sve.size >= broj) { "U bazi nema dovoljno Asocijacije rundi (${sve.size}, treba $broj)" }
        return sve.shuffled().take(broj)
    }

    // ============================================================
    // SEED - jednokratno punjenje baze početnim podacima
    // ============================================================

    /**
     * Ako je neka od kolekcija prazna, puni je početnim podacima.
     * Idempotentno: postojeće kolekcije se ne diraju, pa je bezbedno
     * zvati pri svakom pokretanju aplikacije (1 read po kolekciji).
     */
    suspend fun seedIfEmpty() {
        seedCollection(FirestoreCollections.KZZ_QUESTIONS, seedKzz.map { p ->
            mapOf("tekst" to p.tekst, "odgovori" to p.odgovori, "tacanIndex" to p.tacanIndex)
        })
        seedCollection(FirestoreCollections.SPOJNICE_RUNDE, seedSpojnice.map { r ->
            mapOf(
                "kriterijum" to r.kriterijum,
                "levi" to r.leviPojmovi,
                "desni" to r.desniPojmovi,
                "veze" to r.tacneVeze.entries.associate { it.key.toString() to it.value }
            )
        })
        seedCollection(FirestoreCollections.ASOCIJACIJE_RUNDE, seedAsocijacije.map { r ->
            mapOf(
                "kolone" to mapOf(
                    "A" to r.polja[0], "B" to r.polja[1],
                    "C" to r.polja[2], "D" to r.polja[3]
                ),
                "resenjaKolona" to r.resenjaKolona,
                "finalnoResenje" to r.finalnoResenje
            )
        })
    }

    private suspend fun seedCollection(name: String, docs: List<Map<String, Any>>) {
        val col = db.collection(name)
        if (!col.limit(1).get().await().isEmpty) return   // već popunjena
        val batch = db.batch()
        docs.forEach { batch.set(col.document(), it) }
        batch.commit().await()
    }

    // ============================================================
    // POČETNI PODACI
    // ============================================================

    private val seedKzz = listOf(
        KzzPitanje("Koji je glavni grad Australije?",
            listOf("Sidnej", "Melburn", "Kanbera", "Pert"), 2),
        KzzPitanje("Koje godine je počeo Prvi svetski rat?",
            listOf("1914", "1918", "1939", "1900"), 0),
        KzzPitanje("Koliko planeta ima Sunčev sistem?",
            listOf("7", "8", "9", "10"), 1),
        KzzPitanje("Ko je napisao roman 'Na Drini ćuprija'?",
            listOf("Miloš Crnjanski", "Ivo Andrić", "Branko Ćopić", "Meša Selimović"), 1),
        KzzPitanje("Koji je hemijski simbol za zlato?",
            listOf("Au", "Ag", "Zl", "Go"), 0),
        KzzPitanje("Koja reka protiče kroz Niš?",
            listOf("Morava", "Nišava", "Timok", "Ibar"), 1),
        KzzPitanje("Koliko sekundi ima jedan sat?",
            listOf("360", "3600", "6000", "1440"), 1),
        KzzPitanje("Ko je autor tragedije 'Romeo i Julija'?",
            listOf("Šekspir", "Molijer", "Gete", "Dante"), 0),
        KzzPitanje("Koji je najveći okean na svetu?",
            listOf("Atlantski", "Indijski", "Tihi", "Severni ledeni"), 2),
        KzzPitanje("Koja planeta je najbliža Suncu?",
            listOf("Venera", "Mars", "Merkur", "Zemlja"), 2)
    )

    private val seedSpojnice = listOf(
        SpojniceRundaPodaci(
            kriterijum = "Poveži glavni grad sa državom",
            leviPojmovi = listOf("Pariz", "Tokio", "Madrid", "Atina", "Lisabon"),
            desniPojmovi = listOf("Grčka", "Francuska", "Portugalija", "Japan", "Španija"),
            tacneVeze = mapOf(0 to 1, 1 to 3, 2 to 4, 3 to 0, 4 to 2)
        ),
        SpojniceRundaPodaci(
            kriterijum = "Poveži životinju sa staništem",
            leviPojmovi = listOf("Polarni medved", "Kamila", "Žaba", "Hobotnica", "Sova"),
            desniPojmovi = listOf("Pustinja", "Šuma", "Polarna kapa", "Okean", "Bara"),
            tacneVeze = mapOf(0 to 2, 1 to 0, 2 to 4, 3 to 3, 4 to 1)
        ),
        SpojniceRundaPodaci(
            kriterijum = "Poveži pisca sa delom",
            leviPojmovi = listOf("Andrić", "Šekspir", "Servantes", "Tolstoj", "Orvel"),
            desniPojmovi = listOf("Hamlet", "Rat i mir", "1984", "Na Drini ćuprija", "Don Kihot"),
            tacneVeze = mapOf(0 to 3, 1 to 0, 2 to 4, 3 to 1, 4 to 2)
        ),
        SpojniceRundaPodaci(
            kriterijum = "Poveži državu sa valutom",
            leviPojmovi = listOf("Japan", "Švajcarska", "Velika Britanija", "SAD", "Srbija"),
            desniPojmovi = listOf("Funta", "Dinar", "Jen", "Franak", "Dolar"),
            tacneVeze = mapOf(0 to 2, 1 to 3, 2 to 0, 3 to 4, 4 to 1)
        )
    )

    private val seedAsocijacije = listOf(
        AsocijacijeRundaPodaci(
            polja = listOf(
                listOf("Hrast", "Šuma", "List", "Koren"),
                listOf("Zraci", "Leto", "Žuto", "Ujutru"),
                listOf("Kutija", "Glava", "Paliti", "Sumporna"),
                listOf("Krv", "Ruža", "Jabuka", "Ferari")
            ),
            resenjaKolona = listOf("DRVO", "SUNCE", "ŠIBICA", "CRVENO"),
            finalnoResenje = "VATRA"
        ),
        AsocijacijeRundaPodaci(
            polja = listOf(
                listOf("Žica", "Akord", "Akustična", "Prsti"),
                listOf("Stih", "Refren", "Melodija", "Ritam"),
                listOf("Bina", "Publika", "Ulaznica", "Ovacije"),
                listOf("Linija", "Pauza", "Čitanje", "Ključ")
            ),
            resenjaKolona = listOf("GITARA", "PESMA", "KONCERT", "NOTA"),
            finalnoResenje = "MUZIKA"
        ),
        AsocijacijeRundaPodaci(
            polja = listOf(
                listOf("Gol", "Penal", "Ofsajd", "Dribling"),
                listOf("Koš", "Trojka", "Skok", "Parket"),
                listOf("Mreža", "Smeč", "Servis", "Blok"),
                listOf("Reket", "Loptica", "Gem", "Teren")
            ),
            resenjaKolona = listOf("FUDBAL", "KOŠARKA", "ODBOJKA", "TENIS"),
            finalnoResenje = "SPORT"
        )
    )
}
