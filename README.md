# NulChat — Phase 1 + Phase 2

Kotlin Multiplatform + Compose Multiplatform Android app implementing Phases 1
and 2 from `PROJECT_BRIEF.md`, plus a GitHub Actions workflow that builds the
debug APK on every push.

## Phase 1 (recap)
- [x] Identity generation (Ed25519 keypair via libsodium bindings)
- [x] 24-word BIP-39-style seed phrase generation & recovery
- [x] Local encrypted storage (SQLDelight + SQLCipher, key in Android Keystore)
- [x] Basic Compose UI (onboarding, Discord-style server rail)
- [x] Server creation & listing (persisted locally)

## Phase 2 (this update)
- [x] LAN peer discovery via mDNS/NSD (`android.net.nsd.NsdManager`)
- [x] A minimal TCP transport (`MessageTransport`) with a HELLO + MESSAGE
      frame protocol
- [x] A simplified Double Ratchet (`DoubleRatchetSession`) for 1:1 DM
      encryption, built on X25519 DH + a symmetric chain KDF + SecretBox AEAD
- [x] Direct Messages UI: contacts/peers list + chat screen
- [x] Messages persisted locally (in the same SQLCipher-encrypted DB)
- [ ] Text channels' messages are still local-only/unencrypted placeholders —
      real group messaging needs Sender Keys, which the brief scopes to
      Phase 3 (roles/permissions/moderation) since it's tied to server
      membership

### Important limitations — read before trusting this with real secrets

1. **No X3DH, no identity verification on first contact.** Two devices that
   find each other on the LAN exchange an ephemeral X25519 key *in the
   clear* and derive a shared secret from plain Diffie-Hellman. This stops a
   passive eavesdropper but **not** an active man-in-the-middle on that very
   first handshake — nothing yet proves the peer answering to a given
   peerId controls that peerId's actual Ed25519 identity key. Signal's
   answer to this is "safety numbers": both users compare a short
   fingerprint out of band before trusting a conversation. That's the
   natural next piece of work — see `DirectMessageService.kt`'s doc comment.
2. **Ratchet sessions are in-memory only.** Restarting the app drops all
   active Double Ratchet sessions; the next message with a peer re-runs the
   handshake. The `Peer` table already has columns reserved for persisting
   ratchet state (encrypted, like everything else in the DB) — wiring that
   up is the next step once the in-memory version is confirmed working.
3. **Skipped/out-of-order message keys aren't persisted either** — a message
   that arrives after the app restarts, out of order, becomes undecryptable.
4. **One TCP connection per message, no offline queue.** If the recipient
   isn't online right now, sending just fails. The brief's "relay system"
   (section 5.6) — store-and-forward via volunteer/opportunistic relays —
   is not implemented.
5. **The crypto library's exact method names are a best-effort guess.**
   `com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings` is marked
   experimental by its own author and its API isn't fully published outside
   its source code. Every call into it is isolated in one file,
   `composeApp/src/commonMain/kotlin/com/nulchat/crypto/CryptoPrimitives.kt`
   — if the build fails on a missing method/class there, that's the only
   file that should need touching. Compare against the actual resolved jar
   (Android Studio: Ctrl/Cmd-click into the class) and adjust.

## Before you build

1. **Paste the full BIP-39 wordlist** into
   `composeApp/src/commonMain/kotlin/com/nulchat/identity/Wordlist.kt` (only
   8 sample words are there now). Get the official list from
   `https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt`, same
   order, all 2048 words. The app intentionally crashes on startup via a
   `check()` until this is done.

## Building locally (Android Studio)

No `gradlew`/wrapper jar is committed to this repo. Open the project in a
recent Android Studio (Koala/Ladybug+, with the Kotlin Multiplatform
plugin) — it will offer to generate the wrapper for you on first sync using
its bundled Gradle. Alternatively, if you have Gradle installed locally, run:

```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

## Building via GitHub Actions

`.github/workflows/android-build.yml` builds automatically on every push to
`main` and on pull requests, and can also be run manually (Actions tab ->
"Android Build" -> "Run workflow"). It:

1. Sets up JDK 17
2. Installs Gradle 8.7 directly (via `gradle/actions/setup-gradle`) — no
   wrapper needed; GitHub's `ubuntu-latest` runners already have the Android
   SDK preinstalled
3. Warns (but doesn't fail the build) if `Wordlist.kt` is still incomplete
4. Runs `gradle assembleDebug`
5. Uploads the resulting APK as a downloadable workflow artifact named
   `nulchat-debug-apk`

If you'd rather it fail loudly on an incomplete wordlist instead of just
warning, or you want a **release** build (which needs a signing config —
keystore + secrets in GitHub), say so and we can add that.

## Project layout

```
NulChat/
├── .github/workflows/android-build.yml
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/nulchat/
│       │   │   ├── identity/     <- keypair + seed phrase logic
│       │   │   ├── crypto/       <- CryptoPrimitives + DoubleRatchetSession
│       │   │   ├── storage/      <- expect DatabaseDriverFactory
│       │   │   ├── net/          <- expect PeerDiscovery
│       │   │   ├── data/         <- repository + domain models
│       │   │   └── ui/           <- Compose screens + theme
│       │   └── sqldelight/       <- .sq schema (source of truth for the DB)
│       └── androidMain/
│           ├── AndroidManifest.xml
│           └── kotlin/com/nulchat/
│               ├── MainActivity.kt
│               ├── storage/      <- actual DatabaseDriverFactory (SQLCipher)
│               └── net/          <- actual PeerDiscovery (NSD), MessageTransport,
│                                    DirectMessageService (orchestration)
└── README.md
```

## Suggested next step (Phase 3, per the brief)

- Server join/invite links, member roles & permissions, moderation
  (kick/ban/mute)
- Persisting ratchet sessions + skipped message keys (see limitations above)
- A real fingerprint/safety-number verification step for new peers
