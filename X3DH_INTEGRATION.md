# X3DH — ce s-a adăugat în această sesiune

Înainte de asta, `DoubleRatchetSession` știa doar să continue un secret deja
existent — nu exista niciun mecanism prin care doi peers care nu au vorbit
niciodată să ajungă la primul secret comun. Asta e exact ce face X3DH.

## Fișiere noi (`crypto/`)

- **`X25519KeyPair`** — pereche generică de chei X25519, refolosită pentru
  cheia de identitate, signed prekey și one-time prekeys.
- **`SignedPreKey`** / **`OneTimePreKey`** — cele două tipuri de prekey din
  X3DH, cu `keyId` pentru rotație / identificare în protocol.
- **`PreKeyBundle`** — ce publică un peer ca alții să-i poată scrie primii
  (`serialize()`/`deserialize()`, ca la `DoubleRatchetSession.Envelope`).
- **`X3DHInitialMessage`** — ce trimite inițiatorul ca să completeze
  handshake-ul pe partea celuilalt.
- **`X3DH`** — protocolul propriu-zis: `generateSignedPreKey`,
  `generateOneTimePreKeys`, `initiate` (Alice), `respond` (Bob).

Am verificat matematica (DH1–DH4 + concatenare + HKDF) separat, cu X25519
din Python (`cryptography`), înainte să scriu versiunea Java — ambele părți
ajung la exact același secret, cu și fără one-time prekey.

## Ce s-a modificat

- **`IdentityGenerator`** — `deriveX25519IdentityKeys(identity)`: cheia
  X25519 de identitate (pentru DH) e derivată determinist din același seed
  Ed25519, deci rămâne recuperabilă din cele 24 de cuvinte fără să stocăm
  nimic în plus. E o cheie separată de cea de semnare — Tink nu expune o
  conversie sigură între Ed25519 și X25519.
- **`NulChatDbHelper`** — coloană nouă `x25519IdentityKey` pe `Peer`
  (pinned la primul bundle văzut de la acel peer), plus tabelele
  `SignedPreKey` și `OneTimePreKey`. Fără migrare — la fel ca până acum,
  fiindcă suntem tot pe schema v1.
- **`NulChatRepository`** — secțiune nouă "X3DH prekeys": generare/citire
  signed prekey, pool de one-time prekeys, `buildOurPreKeyBundle(...)`,
  `pinPeerX25519IdentityKey(...)`. Plus `ensurePreKeysExist(identity)`,
  apelată o singură dată, imediat după `saveIdentity(...)`, din
  `RecoveryPhraseActivity` și `OnboardingActivity` (ambele căi: identitate
  nouă și import din frază).

## Ce NU s-a schimbat (rămâne pentru pasul de transport)

- `PreKeyBundle` și `X3DHInitialMessage` nu circulă nicăieri încă — nu
  există niciun canal prin care un bundle să ajungă la celălalt telefon.
  Sunt gata de folosit din momentul în care există un transport.
- Rotația signed prekey-ului și completarea pool-ului de one-time prekeys
  nu sunt implementate — `ensurePreKeysExist` generează o singură dată.
- `ConversationActivity` e neschimbată — tot local, tot cu Toast-ul
  "Not sent". Legarea `X3DH.initiate`/`respond` + `DoubleRatchetSession` de
  butonul de send ține de pasul următor.
- Fluxul vechi de contacte (`ContactsFragment` / `util.ContactRepository`,
  listă în memorie) e tot separat de `data.NulChatRepository` — nu l-am
  atins.

## Self-test (temporar, de șters când există transport)

**`crypto/X3DHSelfTest.java`** — rulează un handshake complet între două
identități generate local, direct pe stack-ul de criptografie real
(Tink), fără rețea și fără să atingă DB-ul real. Verifică: handshake cu
one-time prekey, handshake fără (pool epuizat), respingerea unui bundle cu
semnătură falsificată, respingerea unui bundle cu identitate greșită
(pinning). Aruncă excepție cu detalii la primul eșec; altfel întoarce un
raport scurt.

Legat de un buton nou în **Settings → Debug → "Run X3DH self-test"**
(`SettingsFragment`, `fragment_settings.xml`, string-uri noi în
`strings.xml`) — rulează pe thread separat, arată rezultatul într-un
`AlertDialog`. Șterge tot (clasa + butonul + secțiunea din layout) odată ce
transportul există și testarea reală, pe două telefoane, îl înlocuiește.

## Cum se leagă de `DoubleRatchetSession`

```java
// Alice, după ce a obținut bundle-ul lui Bob (viitor, prin transport):
X3DH.Result r = X3DH.initiate(ourX25519IdentitySecret, bobEd25519PinnedKey, bobBundle);
DoubleRatchetSession session = DoubleRatchetSession.initAsInitiator(
        r.sharedSecret, bobBundle.signedPreKeyPublic);
// + trimite un X3DHInitialMessage(ourX25519IdentityPublic, r.ephemeralPublicKey,
//   bobBundle.signedPreKeyId, r.usedOneTimePreKeyId) către Bob

// Bob, la primirea acelui X3DHInitialMessage:
OneTimePreKey otpk = msg.oneTimePreKeyId == null ? null
        : repo.getOneTimePreKeyIfUnused(msg.oneTimePreKeyId);
byte[] sk = X3DH.respond(ourX25519IdentitySecret, ourSignedPreKey.keyPair.secretKey,
        otpk == null ? null : otpk.keyPair.secretKey,
        msg.identityKeyX25519, msg.ephemeralPublicKey);
DoubleRatchetSession session = DoubleRatchetSession.initAsResponder(
        sk, ourSignedPreKey.keyPair.secretKey, ourSignedPreKey.keyPair.publicKey);
```
