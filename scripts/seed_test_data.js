/**
 * Punjenje baze test podacima za funkcionalnosti Regioni / Lige / Prijatelji.
 *
 * Kreira lažne korisnike (users), njihove rezultate (gameResults) i istoriju
 * plasmana regiona (regionStandings), da bi se mapa, rang liste, statistika
 * regiona, okviri i lige mogli testirati bez ručnog igranja mnogo partija.
 *
 * Koristi Firebase Admin SDK koji ZAOBILAZI security rules, pa ne treba mijenjati
 * firestore.rules. Ovi korisnici NEMAJU Auth nalog (ne mogu se ulogovati) - služe
 * samo da popune prikaze (pretraga, mapa, rang po regionima). Pokretanje: vidi README.md.
 */
const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

// Mora se poklapati sa R.array.regioni i data/Regioni.kt u aplikaciji
const REGIONI = ["Vojvodina", "Beograd", "Šumadija i Zapadna Srbija", "Južna i Istočna Srbija"];
const PRAGOVI = [100, 200, 400, 800, 1600];

function ligaZa(stars) {
  return PRAGOVI.filter((p) => stars >= p).length;
}
function mjesecId(offset = 0) {
  const d = new Date();
  d.setMonth(d.getMonth() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}
function rand(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

const BROJ_KORISNIKA = 14;
const IGRE = ["KO_ZNA_ZNA", "SPOJNICE", "ASOCIJACIJE", "SKOCKO", "MOJ_BROJ", "KORAK_PO_KORAK"];

async function seedKorisnici() {
  const batch = db.batch();
  const kreirani = [];

  for (let i = 1; i <= BROJ_KORISNIKA; i++) {
    const uid = `test_user_${i}`;
    const region = REGIONI[i % REGIONI.length];
    const stars = rand(0, 900);
    const aktivan = i % 3 !== 0; // ~2/3 aktivnih (za "aktivni igrači")

    const user = {
      uid,
      email: `test${i}@slagalica.rs`,
      username: `test_igrac_${i}`,
      region,
      createdAt: Date.now(),
      emailVerified: true,
      avatarId: rand(1, 4),
      tokens: rand(3, 20),
      stars,
      league: ligaZa(stars),
      starsWeekly: rand(0, 120),
      starsMonthly: rand(0, 400),
      lifetimeStars: stars + rand(0, 200),
      tokensFromStars: 0,
      lastDailyGrant: Date.now(),
      lastCycleWeekly: "",
      lastCycleMonthly: mjesecId(),
      lastSeen: aktivan ? Date.now() : Date.now() - 24 * 60 * 60 * 1000,
    };
    const ref = db.collection("users").doc(uid);
    batch.set(ref, user, { merge: true });
    kreirani.push(user);

    // Par rezultata igara (za statistiku profila, ako se gleda tuđi profil kasnije)
    for (let g = 0; g < 3; g++) {
      const igra = IGRE[rand(0, IGRE.length - 1)];
      const moji = rand(0, 50);
      const rez = db.collection("users").doc(uid).collection("gameResults").doc();
      batch.set(rez, {
        gameType: igra,
        myPoints: moji,
        opponentPoints: rand(0, 50),
        won: moji >= 25,
        playedAt: Date.now() - rand(0, 20) * 86400000,
        details: {},
      });
    }
  }

  await batch.commit();
  console.log(`✔ Kreirano ${kreirani.length} test korisnika (+ po 3 rezultata).`);
  return kreirani;
}

async function seedRegionStandings(korisnici) {
  // Trenutni poredak po zbiru starsMonthly + par prošlih ciklusa (za 5.d "mjesta" i 5.e okvire)
  const zbir = {};
  REGIONI.forEach((r) => (zbir[r] = 0));
  korisnici.forEach((u) => (zbir[u.region] += u.starsMonthly));
  const poredak = [...REGIONI].sort((a, b) => zbir[b] - zbir[a]);

  // Prethodni mjesec = poredak izveden iz podataka; još 2 ranija = nasumične permutacije
  const ciklusi = [mjesecId(-1), mjesecId(-2), mjesecId(-3)];
  const batch = db.batch();
  ciklusi.forEach((c, idx) => {
    const p = idx === 0 ? poredak : [...REGIONI].sort(() => Math.random() - 0.5);
    batch.set(db.collection("regionStandings").doc(c), { poredak: p });
  });
  await batch.commit();
  console.log(`✔ Arhivirani plasmani regiona za: ${ciklusi.join(", ")}`);
}

(async () => {
  try {
    const korisnici = await seedKorisnici();
    await seedRegionStandings(korisnici);
    console.log("\n✅ Gotovo. Otvori app -> Regioni / Lige / Prijatelji (pretraga: 'test').");
    process.exit(0);
  } catch (e) {
    console.error("Greška pri punjenju:", e);
    process.exit(1);
  }
})();
