# Firebase – uputstvo za podešavanje

Aplikacija sada koristi **Firebase** za:

| Funkcionalnost | Firebase servis | Gdje u kodu |
|---|---|---|
| 1. Registracija i logovanje | Firebase Auth + Firestore | `data/AuthRepository.kt`, `viewmodel/AuthViewModel.kt` |
| 11. Notifikacije | Firebase Cloud Messaging + Firestore | `data/SlagalicaMessagingService.kt`, `data/NotifikacijeRepository.kt`, `data/NotificationChannels.kt` |
| Igre: Skočko, Korak po korak, Moj broj | Firestore | `data/GameResultRepository.kt` + odgovarajući ViewModel-i |

## Šta MORAŠ uraditi prije pokretanja

Fajl `app/google-services.json` je trenutno **placeholder** i mora se zamijeniti pravim:

1. Otvori https://console.firebase.google.com i napravi projekat.
2. Dodaj Android aplikaciju sa package imenom **`com.example.slagalica`**.
3. Preuzmi `google-services.json` i zamijeni njime postojeći u `app/` folderu.
4. U Firebase konzoli uključi:
   - **Authentication → Sign-in method → Email/Password** (uključujući verifikaciju mejlom).
   - **Firestore Database** (kreiraj bazu, npr. u test modu za razvoj).
   - **Cloud Messaging** (automatski je dostupan).

## Struktura podataka u Firestore

```
users/{uid}                      -> { uid, email, username, region, createdAt, emailVerified, fcmToken }
usernames/{korisnickoIme}        -> { email, uid }            // za login po korisničkom imenu
users/{uid}/notifications/{id}   -> { title, content, category, timestampMs, read }
users/{uid}/gameResults/{id}     -> { gameType, myPoints, opponentPoints, won, playedAt }
```

## Napomene

- Izvorni kod je premješten iz `src/main/java/` u **`src/main/kotlin/`** (projekat je u potpunosti Kotlin). Gradle to čita preko `sourceSets { main.kotlin.srcDirs += 'src/main/kotlin' }`.
- Login radi i mejlom i korisničkim imenom (1.d). Za ime se prvo iz `usernames` kolekcije pronađe mejl.
- Ulazak u aplikaciju je moguć tek nakon potvrde mejla (1.c) – nepotvrđeni nalozi se odjavljuju uz poruku.
- Promjena lozinke (1.e) je u `AuthViewModel.changePassword(stara, nova)` – traži reautentifikaciju starom lozinkom.
- Neregistrovani igrač može da igra; rezultati igara se za njega prosto ne snimaju (`GameResultRepository` to tiho preskoči).
