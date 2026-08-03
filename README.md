# Onboarding conectat la identitatea reală

## Ce s-a schimbat

- **`OnboardingActivity.java`** (rescris) — "Create new identity" acum
  generează un `SeedPhrase` real + `Identity` via `IdentityGenerator`, apoi
  trece la ecranul de recovery phrase (nu salvează încă). "Import" cere
  cele 24 de cuvinte, le validează (checksum BIP-39), reconstruiește
  identitatea și salvează direct.
- **`RecoveryPhraseActivity.java`** (nou) — arată cele 24 de cuvinte
  numerotate, cere bifarea "le-am notat" înainte de a activa butonul de
  continuare, abia atunci salvează identitatea în DB.
- **`storage/AppDatabase.java`** (nou) — un singur punct de acces la baza
  de date criptată pentru tot procesul (deschide o dată, refolosește).
- **`ProfileFragment.java`** — peer ID-ul afișat acum e cel real, citit din
  DB, nu mai e placeholder.
- **`util/IdentityStore.java`** — curățat de metodele de identitate falsă
  (nu mai există `hasIdentity()`/`createPlaceholderIdentity()`); a rămas
  doar ce era oricum legitim: nickname, bio, status, accent, setări releu.
- **`AndroidManifest.xml`** — adăugat `RecoveryPhraseActivity`.
- **`strings.xml`** — string-uri noi pentru ecranul de recovery phrase și
  mesaje de eroare.

## Unde pui fișierele

Toate păstrează calea din `app/src/main/...` — suprascrie direct peste
fișierele cu același nume din repo. `AppDatabase.java` e singurul chiar
nou (nu exista înainte).

## O decizie pe care am luat-o știind că nu-i completă

Nickname-ul de profil rămâne salvat doar în `SharedPreferences`
(`IdentityStore`), nu și în coloana `displayName` din `IdentityRow`. Sunt
două locuri diferite care ambele ar putea ține un "nume afișat" — nu le-am
sincronizat ca să nu complic scope-ul acestui pas. Nu e un bug, doar o
redundanță minoră; dacă vrei, o rezolvăm quick quando ajungem la
comunități/servere (unde numele afișat chiar contează pentru alți peers).

## Ce NU s-a schimbat (rămâne pentru X3DH)

Fluxul de mai sus generează/recuperează identitatea proprie. Nu atinge
deloc handshake-ul între doi peers care nu au vorbit niciodată — asta
rămâne exact pasul următor.
