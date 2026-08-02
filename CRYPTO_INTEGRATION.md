# MuleChat — criptografie + identitate restaurate și integrate

## Ce am găsit

Fișierele de criptografie descrise în README-ul vechi (`CryptoPrimitives`,
`DoubleRatchetSession`, `Identity`/`IdentityGenerator`, `SeedPhrase`/
`Wordlist`, `NulChatRepository`, `NulChatDbHelper`, `PassphraseProvider`)
nu mai erau în proiectul pe care mi l-ai trimis — probabil s-au pierdut
când sincronizarea din AIDE a suprascris folderul `app/` cu shell-ul de UI.
Le-am recuperat din istoricul git (erau commit-uite la un moment dat,
git nu uită) și le-am revizuit. Sunt scrise bine — am continuat pe ele,
nu le-am rescris.

## Ce am schimbat în acest pas

- **Pachet unificat**: fișierele foloseau `com.nulchat.*`, aplicația
  folosește `com.mulechat.app`. Le-am mutat sub
  `com.mulechat.app.{crypto,identity,storage,data}` ca să nu existe două
  identități de aplicație în același proiect.
- **Dependențe adăugate** în `app/build.gradle`: Tink, SQLCipher,
  Jetpack Security (`security-crypto`) — exact versiunile din README-ul
  vechi.
- **Persistența sesiunilor** (unul din golurile semnalate): am adăugat
  `RatchetSession` ca tabelă nouă în baza de date, plus
  `serialize()`/`deserialize()` pe `DoubleRatchetSession` și
  `saveRatchetSession`/`loadRatchetSession`/`deleteRatchetSession` în
  `NulChatRepository`. O conversație acum poate supraviețui unui restart
  al aplicației — apelează `saveRatchetSession` după fiecare `encrypt()`
  și `decrypt()`, nu doar la închiderea curată a aplicației, altfel un
  crash între mesaje tot poate pierde o cheie "sărită".

## Un pas manual rămas (nu am putut să-l fac eu)

`Wordlist.java` are doar 8 cuvinte placeholder — inițializatorul static
aruncă eroare intenționat până e completă. Mediul meu de sandbox nu a
reușit să tragă fișierul brut de 2048 de cuvinte (GitHub blochează
accesul automat la `raw.githubusercontent.com`, iar vizualizarea normală
se oprește la 1000 de linii pentru fișiere mari). E un fișier de date,
nu ceva ce am nevoie să "scriu" — cel mai simplu e să-l iei tu direct:

```bash
curl -o english.txt https://raw.githubusercontent.com/bitcoin/bips/master/bip-0039/english.txt
```

apoi lipești cele 2048 de cuvinte (un cuvânt pe linie în fișierul
descărcat) în array-ul `WORDS` din `Wordlist.java`, în ordinea exactă din
fișier — ordinea contează, e parte din standard.

## Ce NU e făcut încă (rămâne pentru pasul următor)

- **X3DH** — sesiunile Double Ratchet pornesc dintr-un secret comun deja
  existent; nu există încă niciun mecanism prin care doi peers care nu
  au vorbit niciodată să ajungă la acel prim secret. Are nevoie de un
  pachet de "prekeys" semnate, publicat undeva accesibil (de exemplu prin
  releul Python), plus un pas de acord de chei rulat o singură dată per
  contact nou.
- **Onboarding-ul din UI încă folosește identitatea falsă** (`IdentityStore`
  din `util/`, hex random). Acum există un `IdentityGenerator` real
  alături, dar nu le-am conectat în acest pas — asta înseamnă și un flux
  de afișare + confirmare a frazei de recuperare (24 de cuvinte), plus
  deschiderea bazei de date cu `PassphraseProvider` la pornirea
  aplicației. E lucru de UI + wiring, nu de criptografie, dar suficient
  cât să merite un pas separat ca să nu-l fac în grabă.
- Transport (LAN/relay) — neschimbat față de înainte.

## Fișiere noi/schimbate

```
app/src/main/java/com/mulechat/app/
├── crypto/CryptoPrimitives.java       (recuperat, pachet schimbat)
├── crypto/DoubleRatchetSession.java   (recuperat + persistență adăugată)
├── identity/Identity.java             (recuperat, pachet schimbat)
├── identity/IdentityGenerator.java    (recuperat, pachet schimbat)
├── identity/SeedPhrase.java           (recuperat, pachet schimbat)
├── identity/Wordlist.java             (recuperat -- ție-i rândul, vezi mai sus)
├── storage/NulChatDbHelper.java       (recuperat + tabelă RatchetSession)
├── storage/PassphraseProvider.java    (recuperat, pachet schimbat)
├── data/NulChatRepository.java        (recuperat + metode ratchet session)
├── data/PeerContact.java, Server.java, Channel.java, DirectMessage.java
app/build.gradle                       (+ Tink, SQLCipher, security-crypto)
```
