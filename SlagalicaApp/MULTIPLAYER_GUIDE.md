# Multiplayer preko Firestore-a (bez servera)

Dva telefona igraju jedan protiv drugog koristeći **samo Firebase** — Firestore
služi kao realtime posrednik. Nema C# (ni bilo kojeg drugog) servera.

## Kako radi

```
Telefon A ─┐                          ┌─ Telefon B
           ├──►  Firestore: matches/{id}  ◄──┤
           │     (oba slušaju u realnom vremenu)
        upiše potez                     upiše potez
           └──────────►  HOST (player1) boduje rundu  ◄──────────┘
```

1. Matchmaking: igrač se prijavi u kolekciju `matchmaking`. Ako neko već čeka,
   transakcija ih spaja u dokument `matches/{id}` i briše/označava tiket.
2. Oba telefona slušaju `matches/{id}` (snapshot listener) — svaka promjena
   stiže oba u realnom vremenu.
3. Svaki igrač lokalno odigra rundu i upiše svoje poteze u dokument.
4. Kad su oba odigrala, **host (player1)** izračuna poene (po specifikaciji),
   upiše ih i pomjeri meč na sljedeću rundu. Na kraju proglasi pobjednika.

Host = `player1` (onaj ko je prvi čekao). Drugi telefon samo prati promjene.

## Struktura podataka (Firestore)

```
matchmaking/{uid}        -> { uid, name, status: "waiting"|"matched", matchId }
matches/{matchId}        -> {
    player1Id, player1Name, player2Id, player2Name,
    status: "in_progress"|"finished",
    currentRoundIndex,
    rounds: [ {
        gameType: "Skocko",
        roundNumber, starterId,
        config: { secret: [Int,Int,Int,Int] },     // tajna kombinacija
        p1Sub: { guesses: ["0,3,1,2", ...] } | null,
        p2Sub: { guesses: [...] } | null,
        p1Points, p2Points, resolved
    } ],
    player1Score, player2Score, winnerId
}
```

## Fajlovi

| Fajl | Uloga |
|---|---|
| `data/GameLogic.kt` | čista logika i bodovanje sve tri igre (po specifikaciji) |
| `data/MultiplayerRepository.kt` | matchmaking, sinhronizacija, slanje poteza, host bodovanje |
| `model/MatchState.kt` | tipizirano stanje meča/runde iz Firestore dokumenta |
| `viewmodel/MultiplayerViewModel.kt` | dijeljeni VM (matchmaking + živi meč) |
| `ui/main/IgrajFragment.kt` | dugme „Igraj" -> matchmaking -> navigacija na meč |
| `ui/games/SkockoMpFragment.kt` | multiplayer Skočko ekran |
| `firestore.rules` | sigurnosna pravila (postaviti u konzoli) |

## Kako testirati sa dva telefona

1. U Firebase konzoli: Authentication (Email/Password) + Firestore + ova `firestore.rules`.
2. Napravi **dva naloga** (dva mejla), potvrdi oba.
3. Instaliraj app na dva telefona/emulatora, prijavi se sa po jednim nalogom.
4. Na oba pritisni „Igraj". Prvi ide u čekanje, drugi ga spoji — oba uđu u Skočko.
5. Igrajte; rezultati i skor se sinhronizuju u realnom vremenu.

> Ako nemaš dva fizička telefona: pokreni dva emulatora, ili jedan emulator + jedan telefon.

## Trenutni obim i proširenje

Sada je povezana igra **Skočko (2 runde)** kao radni primjer cijelog toka.
Logika za **Korak po korak** i **Moj broj** je već u `GameLogic.kt`. Da ih dodaš u meč:

1. U `MultiplayerRepository.buildMatchData(...)` dodaj njihove runde u listu
   (gameType + odgovarajući `config`: za Korak `{word, hints}`, za Moj broj `{target, numbers}`).
2. Dodaj `submitKorak(...)` / `submitMojBroj(...)` u repozitorij i VM (analogno `submitSkocko`).
3. Proširi `hostResolveIfReady` da bira `GameLogic.resolveKorak` / `resolveMojBroj`
   prema `gameType` runde.
4. Napravi `KorakMpFragment` / `MojBrojMpFragment` po uzoru na `SkockoMpFragment`,
   i u `SkockoMpFragment`/navigaciji pređi na sljedeću igru kad se promijeni `gameType`.

## Važna napomena (anti-cheat)

Pošto nema servera, tajna kombinacija stoji u dijeljenom dokumentu koji oba
telefona čitaju. Za projekat je to u redu. Za pravi anti-cheat tajna ne bi smjela
da bude na klijentu — tada je potreban server (ili Cloud Functions), što je
kompromis koji smo svjesno izabrali biranjem „samo Firebase" pristupa.
