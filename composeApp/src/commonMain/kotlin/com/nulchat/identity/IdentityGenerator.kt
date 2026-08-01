package com.nulchat.identity

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.generichash.GenericHash
import com.ionspin.kotlin.crypto.signature.Signature

/**
 * Generates and reconstructs identities. All calls are suspend because
 * libsodium's WASM/JS bindings load asynchronously on some platforms;
 * on Android it resolves immediately.
 */
object IdentityGenerator {

    /** Must be called once before any other function in this object. */
    suspend fun ensureReady() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }

    /** Creates a brand-new identity with a fresh Ed25519 keypair. */
    suspend fun generate(): Identity {
        ensureReady()
        val keyPair = Signature.keypair()
        val publicKey = keyPair.publicKey.toByteArray()
        val privateKey = keyPair.secretKey.toByteArray()
        return Identity(
            publicKey = publicKey,
            privateKey = privateKey,
            peerId = derivePeerId(publicKey)
        )
    }

    /**
     * Rebuilds an identity from a 24-word seed phrase (see [SeedPhrase]).
     * Ed25519 keys in libsodium are deterministically derivable from a
     * 32-byte seed, so entropy -> seed -> keypair is reproducible.
     */
    suspend fun fromSeedPhrase(words: List<String>): Identity {
        ensureReady()
        val entropy = SeedPhrase.toEntropy(words) // 32 bytes
        val keyPair = Signature.seedKeypair(entropy.toUByteArray())
        val publicKey = keyPair.publicKey.toByteArray()
        val privateKey = keyPair.secretKey.toByteArray()
        return Identity(
            publicKey = publicKey,
            privateKey = privateKey,
            peerId = derivePeerId(publicKey)
        )
    }

    /**
     * peerId = base32(blake2b(publicKey, 20 bytes)) — short, shareable, unique.
     *
     * NOTE: this library is experimental and its exact method signatures have
     * shifted between versions (see CryptoPrimitives.kt for the single place
     * that should be checked/adjusted against the resolved library version).
     */
    private fun derivePeerId(publicKey: ByteArray): String {
        val digest = GenericHash.hash(message = publicKey.toUByteArray(), key = null, hashLength = 20)
        return Base32.encode(digest.toByteArray()).lowercase()
    }
}

/** Minimal Base32 (RFC 4648, no padding) — enough for a compact peer ID string. */
private object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var bits = 0
        var value = 0
        for (b in data) {
            value = (value shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(value shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET[(value shl (5 - bits)) and 0x1F])
        }
        return sb.toString()
    }
}
