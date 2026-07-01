# Seed skripta za test podatke

Puni Firestore lažnim korisnicima, rezultatima i istorijom plasmana regiona,
da bi se **Regioni**, **Lige** i **Prijatelji** mogli testirati bez ručnog
kreiranja mnogo naloga i igranja partija.

> Lažni korisnici nemaju Auth nalog (ne mogu se ulogovati) — služe samo da
> popune prikaze: mapu regiona, rang liste, pretragu prijatelja, statistiku.
> Za testiranje *igranja* i dalje trebaju 2 prava naloga.

## Preduslovi
- Instaliran [Node.js](https://nodejs.org) (LTS).

## Koraci

1. **Preuzmi ključ servisnog naloga** (jednom):
   - Firebase konzola → ⚙ *Project settings* → *Service accounts*
   - *Generate new private key* → sačuvaj fajl kao `scripts/serviceAccountKey.json`
   - ⚠️ Ovaj fajl je tajna — **ne commit-uj ga** (već je u `.gitignore`).

2. **Instaliraj zavisnost i pokreni** (iz `scripts/` foldera):
   ```bash
   cd scripts
   npm install firebase-admin
   node seed_test_data.js
   ```

3. Otvori aplikaciju → **Regioni / Lige / Prijatelji**. U pretrazi prijatelja
   ukucaj `test` da nađeš lažne igrače.

## Ponovno pokretanje / čišćenje
- Skripta koristi `merge`, pa ponovno pokretanje samo osvježi iste `test_user_*`.
- Za brisanje: u Firebase konzoli obriši dokumente `users/test_user_*` i
  kolekciju `regionStandings` (ili cijelu bazu u test modu).

## Šta se kreira
- `users/test_user_1..14` — razni regioni, zvezde, lige, avatari, `lastSeen`
  (dio "aktivnih"), `starsMonthly` (za rang po regionima).
- `users/{id}/gameResults` — po 3 rezultata svakom.
- `regionStandings/{mjesec}` — plasman regiona za prošla 3 ciklusa
  (za "broj 1./2./3. mjesta" i zlatne/srebrne/bronzane okvire avatara).
