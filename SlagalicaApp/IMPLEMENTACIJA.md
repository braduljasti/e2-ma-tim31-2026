# Implementirano: 3. Igranje partija, 4. Rang lista, 8. Čet
### (spojeno sa kolegininim radom na 5. Regioni, 6. Lige, 7. Prijatelji)

Ovaj dokument opisuje šta je dodato/izmenjeno na osnovi koju je kolega/koleginica napravio/la
(HANDOFF_STUDENT1.md), i kako se moj deo uklapa sa njenim/njegovim.

## Kako se uklapa sa kolegininim radom (bitno za odbranu)

Kolegina osnova je već imala `ProgressionRepository.applyMatchResult(won, totalPoints)` koja
dodeljuje zvezde/tokene za partiju (spec 3.d) **i** automatski računa ligu (spec 6.d), plus
`reconcileOnStart()` koja svaki dan dodeljuje tokene (5 + bonus lige, spec 3.a/6.b) i resetuje
`starsWeekly`/`starsMonthly` na promeni ciklusa. Zato **nisam pravio svoj sistem nagrada od nule**
- kad se partija završi, ja samo pozivam njenu funkciju (`MultiplayerRepository.primeniNagraduAkoTreba`
  poziva `ProgressionRepository().applyMatchResult(...)`), i dodajem svoj sloj oko toga:
  - idempotentnost preko `p1RewardApplied`/`p2RewardApplied` polja na samom meču (da se nagrada
    ne primeni dvaput ako klijent više puta osluškuje isti završen meč),
  - poseban slučaj za partije koje su napuštene (spec 3.f - napustio = ne dobija zvezde).
- Rang lista (4) čita **direktno** `users.starsWeekly` / `users.starsMonthly` (kolegina polja) -
  nema posebne kolekcije za rang listu, pa je uvek "uživo" tačna i ne duplira podatke.
- Dnevni tokeni (3.a) i njihov iznos po ligi (6.b) idu isključivo kroz kolegin
  `ProgressionRepository.reconcileOnStart()` - moj `ProfilRepository` sada ima samo `potrosiToken()`
  (trošenje 1 tokena za start partije) i `slusajKorisnika()` (uživo profil za ekran "Igraj").

## 3. Igranje partija

- `MultiplayerRepository.GAME_PARTIJA` - runde svih 6 igara nadovezane jedna za drugom, tačnim
  redosledom iz specifikacije. Postojeća logika bodovanja/rundi po igri je samo refaktorisana u
  6 privatnih `buildXRounds(...)` funkcija, bez izmena.
- **`PartijaMpFragment`** (`ui/main/`) - "host" fragment koji prati koja je igra na redu
  (`currentRound.gameType`) i menja prikazani pod-fragment, koristeći **već postojeće**
  `SkockoMpFragment`, `KzzMpFragment`, `SpojniceMpFragment`, `AsocijacijeMpFragment`,
  `KorakPoKorakMpFragment`, `MojBrojMpFragment` - bez izmene njihove igre logike (samo jedan red
  izmenjen u svih 6, da ne prikazuju svoj "kraj igre" dijalog dok su unutar partije).
- **Tokeni**: `ProfilRepository.potrosiToken()` (transakcija) - dugme "Igraj!" prvo potroši 1
  token pa tek onda pokrene matchmaking; ako nema tokena, prikazuje se poruka.
- **Nagrade** na kraju partije idu kroz kolegin `ProgressionRepository.applyMatchResult` (vidi
  gore) - dijalog na kraju partije prikazuje zvezde, tokene i promenu lige
  (`MatchRewardOutcome.promoted/relegated`).
- **Napuštanje partije**: dugme "Napusti" odmah završava meč (`leaveMatch`), protivnik se odmah
  proglašava pobednikom bez čekanja, a igrač koji je napustio ne dobija zvezde.
  *Pojednostavljenje*: specifikacija dozvoljava da protivnik nastavi da igra sve preostale igre
  solo; zbog velike dodatne složenosti odlučeno je da se meč odmah završi umesto toga.
- Rezultati partije se čuvaju kao **6 odvojenih `GameResult` zapisa** (grupisano po
  `round.gameType`), tako da ekran statistike u Profilu radi bez izmena.

**Van obima**: pozivanje prijatelja na partiju (spec 7.c-e) - kolegin `FriendsRepository` i
`PresenceRepository` (online status) postoje, ali sama integracija poziva u matchmaking nije
urađena (eksplicitno ostavljeno u HANDOFF_STUDENT1.md kao sledeći korak).

## 4. Rang lista

- `RangListaRepository.ucitajRangListu(ciklus)` - upit direktno nad `users` kolekcijom, sortiran
  po `starsWeekly`/`starsMonthly` opadajuće (Firestore ovo automatski indeksira, nije potreban
  ručni indeks jer je `where` i `orderBy` na istom polju).
- `RangListaFragment` - tabovi Nedeljno/Mesečno, opseg datuma ciklusa, automatsko osvežavanje na
  2 minuta (tačno po specifikaciji).
- **Nagrade na kraju ciklusa (4.c) i kazna od 30% za neplasirane (6.e)**:
  `RangListaRepository.pripremiZavrsetakCiklusaAkoTreba(uid)` - poziva se u `MainActivity`
  **PRE** kolegininog `ProgressionRepository.reconcileOnStart()` (bitan redosled! inače bi
  reconcile već resetovao `starsWeekly`/`starsMonthly` na 0 pre nego što stignemo da vidimo
  plasman prošlog ciklusa). Idempotentno preko `users.rewardedCycles`.
- Kao i kod kolegininog `RegionRepository.arhivirajProsliCiklusAkoTreba()`, ovo je **najbolja
  moguća procena u trenutku pokretanja aplikacije**, ne savršen snapshot - svaki korisnik nezavisno
  detektuje kraj ciklusa kad prvi put otvori app, pa je poredak koji vidi približan (isti
  kompromis koji je projekat već svesno prihvatio zbog "samo Firebase, bez servera" pristupa).

## 8. Čet

- `ChatRepository` - poruke po regionu igrača: `chats/{region}/poruke`, realtime
  `addSnapshotListener`, poslednjih 200 poruka.
- `ChatFragment` - RecyclerView sa dva tipa "bubble"-ova (svoje poruke desno, tuđe levo, sa imenom
  pošiljaoca i vremenom), polje za unos + dugme Pošalji.

**Napomena**: push notifikacija drugom igraču kad je van aplikacije bi zahtevala server (Cloud
Functions/Admin SDK) - van izabranog "samo Firebase" pristupa. Poruke stižu u realnom vremenu dok
je Čet ekran otvoren.

## Šta je potrebno uraditi pre testiranja

1. Deploy-ovati ažurirani `firestore.rules` (dodato je `chats/*`, pored kolegininih
   `friendships/*` i `regionStandings/*`).
2. Testirati partiju sa 2 naloga - glavno dugme "Igraj!" pokreće pravu partiju od 6 igara i troši
   1 token; staro biranje pojedinačne igre je premešteno na "Vežbaj pojedinačnu igru" (bez tokena,
   bez uticaja na zvezde/rang listu/ligu - samo za vežbu).

## Poznato kozmetičko ograničenje

Dok se igra partija, brojač poena u zaglavlju svake pojedinačne igre (npr. Skočko ekran unutar
partije) i dalje prikazuje 0-0 dok cela partija ne završi (to polje se puni tek na kraju meča).
Tačan **ukupan** živi skor cele partije se ispravno prikazuje u gornjoj traci
`PartijaMpFragment`-a iznad.
