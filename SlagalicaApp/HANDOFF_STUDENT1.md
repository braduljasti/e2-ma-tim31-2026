# Handoff: integracija sa dijelom Studenta 2 (Regioni / Lige / Prijatelji)

Student 2 je implementirao funkcionalnosti **5 (Regioni)**, **6 (Lige)** i **7 (Prijatelji)**
uz "samo Firebase, bez servera" pristup (lazy reconcile). Ovaj dokument objašnjava
šta treba da uradiš da se tvoj dio (3. Igranje partija, 4. Rang lista, 8. Čet)
uklopi, i šta je namjerno ostavljeno tebi.

---

## 1. OBAVEZNO: objavi nova Firestore pravila

U [firestore.rules](firestore.rules) su dodate kolekcije `friendships` i `regionStandings`.
Otvori Firebase konzolu → Firestore → Rules → nalijepi sadržaj tog fajla → **Publish**.
Bez toga prijatelji i istorija regiona neće raditi.

Nove kolekcije:
- `friendships/{parId}` — obostrano prijateljstvo (`parId` = sortirani par uid-ova, polje `users: [a, b]`).
- `regionStandings/{cycleId}` — poredak regiona po ciklusu (polje `poredak: [naziv...]`).
- `users/{uid}` je dobio nova polja: `stars, league, starsWeekly, starsMonthly,
  lifetimeStars, tokensFromStars, lastDailyGrant, lastCycleWeekly, lastCycleMonthly,
  lastSeen, avatarId, tokens`. Postojeći nalozi rade i bez njih (default vrijednosti).

## 2. Kraj partije → pozovi `applyMatchResult` (najvažnije)

Kad tvoja **cijela partija** završi, za svakog igrača (na njegovom uređaju) pozovi:

```kotlin
val outcome = ProgressionRepository().applyMatchResult(
    won = mojUkupanRezultat > protivnikUkupanRezultat,
    totalPoints = mojUkupanRezultat        // zbir bodova svih 6 igara
)
outcome?.let {
    // it.deltaStars, it.tokensAwarded, it.promoted / it.relegated (za dijalog 6.f)
}
```

- Zovi **jednom po partiji**, iz korutine. Svaki igrač upisuje SVOJ rezultat.
- **Ne zovi za prijateljske partije** (spec 3.e — ne donose zvezde).
- Ovo dodjeljuje zvezde/tokene (3.d) i automatski računa ligu (6.d). Zvezde koje
  upiše pune i rang po regionima (5.b) i lige (6) — moje funkcionalnosti to čitaju.

## 3. Namjerno ostavljeno tebi (zavisi od tvog dijela)

| Spec | Šta fali | Gdje se kači |
|---|---|---|
| 7.c–e | **Pozivi prijatelja na partiju** (poziv → notifikacija → prihvati/odbij, 10s auto-odbijanje, prekid) | treba tvoj matchmaking/„cijela partija" + online status; prijatelji i njihov `lastSeen` (online) su već tu — `FriendsRepository`, `PresenceRepository` |
| 6.e | **−30% zvezda** ako se igrač ne plasira na mjesečnoj rang listi | dodaj u `ProgressionRepository.reconcileOnStart()` kad se mjesečni ciklus promijeni — treba tvoja rang lista (4) da se zna ko se plasirao |
| 6.f | **Notifikacija** o prelasku lige kad korisnik nije u app-u | `applyMatchResult` već vraća `promoted/relegated`; dijalog u app-u je lako, za push treba tvoja FCM infrastruktura |
| 4 | **Globalna rang lista** (nedeljna/mjesečna) | koristi `users.starsWeekly` / `starsMonthly` koje `applyMatchResult` i reconcile već održavaju |

## 4. Nove zavisnosti (u [app/build.gradle](app/build.gradle))
- `com.journeyapps:zxing-android-embedded` — skeniranje QR koda (prijatelji).
- `org.osmdroid:osmdroid-android` — mapa regiona.
- Nove permisije u manifestu: `CAMERA`, `ACCESS_NETWORK_STATE`.

## 5. Lakše testiranje: seed skripta
Vidi [../scripts/README.md](../scripts/README.md) — Node skripta koja puni bazu lažnim
korisnicima, rezultatima i istorijom regiona (za mapu, rang liste, lige, okvire),
bez ručnog igranja. Koristi Firebase Admin SDK (ne dira pravila).
