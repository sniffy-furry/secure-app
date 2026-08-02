# NulChat — Java rewrite

This replaces the earlier Kotlin Multiplatform/Compose version. Reasons for
the switch (see chat history if you want the full reasoning):

- **AIDE Pro compatibility**: single Gradle module, plain Java, classic XML
  layouts — no Kotlin Multiplatform, no Compose, nothing AIDE struggles with.
- **UI**: native Android Views (XML), not WebView/HTML — avoids an entire
  category of risk (XSS-via-message-content reaching a JS↔Java bridge,
  `file://` URL quirks, shipping a second JS engine's worth of attack
  surface). See chat history for the fuller WebView-vs-native comparison.
- **Crypto**: hand-rolled Double Ratchet again (like the Kotlin version),
  but now built on **Tink** (`com.google.crypto.tink:tink-android`) instead
  of an experimental libsodium binding. Tink is Apache 2.0 — deliberately
  *not* Signal's official `libsignal`, which is AGPLv3 and would force
  source disclosure to anyone who receives the APK. Since this app is meant
  to stay closed-source, that ruled libsignal out.

## Status: identity + crypto core only (this is a first slice)

Done so far:
- [x] Project skeleton (Groovy Gradle, single `app` module)
- [x] `CryptoPrimitives.java` — the one file that talks to Tink directly
- [x] `Identity` / `IdentityGenerator` — Ed25519 keypair, deterministic
      recovery from a seed via HKDF (Tink doesn't support seeded key
      generation directly, so the seed is HKDF'd into an Ed25519 seed first)
- [x] `SeedPhrase` / `Wordlist` — BIP-39-style 24-word recovery (ported
      from the Kotlin version)
- [x] `DoubleRatchetSession` — ported from the Kotlin version, with one
      improvement: the message header (sender ratchet key, chain lengths,
      message number) is now bound into the AEAD's associated data, so
      tampering with those fields breaks authentication instead of quietly
      corrupting ratchet state. Same known limitations as before though: no
      X3DH, sessions are in-memory only, skipped keys aren't persisted — see
      the class's doc comment.
- [x] Encrypted local storage — `NulChatDbHelper` (plain
      `net.sqlcipher.database.SQLiteOpenHelper`, no SQLDelight/KMP) +
      `NulChatRepository` (blocking CRUD methods — call from a background
      thread, never the UI thread) + POJOs in `data/`

Not done yet (next slices):
- [ ] LAN discovery (NSD) + TCP transport, ported from the Kotlin version
- [ ] All the actual UI: XML layouts + Activities/Fragments for onboarding,
      server list, contacts, chat

## Before you build

Paste the full 2048-word BIP-39 English list into
`app/src/main/java/com/nulchat/identity/Wordlist.java` (only 8 sample words
are there now) — from
`https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt`, same
order. The app's static initializer intentionally throws until this is done.

## API accuracy note

Every Tink method name used here (`X25519.generatePrivateKey/publicFromPrivate/
computeSharedSecret`, `Hkdf.computeHkdf`, `Ed25519Sign.KeyPair.newKeyPairFromSeed`,
`ChaCha20Poly1305`) was checked against Tink's actual published source before
being used — unlike the earlier libsodium-based version, these aren't
best-effort guesses. `X25519` is still marked `@Alpha` by Tink itself (their
internal "not promoted to stable" marker), which is worth knowing even
though it's the standard way to get raw X25519 out of Tink.

## Building

Same as before: no `gradlew` committed (Android Studio/AIDE generates it on
first sync, or `gradle wrapper --gradle-version 8.7` if you have Gradle
locally). GitHub Actions (`.github/workflows/android-build.yml`) builds a
debug APK on every push using a directly-installed Gradle 8.7, no wrapper
needed, and uploads it as an artifact.
