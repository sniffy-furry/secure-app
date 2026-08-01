package com.nulchat.crypto

import com.ionspin.kotlin.crypto.kdf.Kdf
import com.ionspin.kotlin.crypto.randombytes.LibsodiumRandom
import com.ionspin.kotlin.crypto.scalarmult.ScalarMultiplication
import com.ionspin.kotlin.crypto.secretbox.SecretBox

/**
 * Every direct call into `com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings`
 * lives in this file and nowhere else.
 *
 * WHY: this library is explicitly marked "experimental" by its author and its
 * public API has changed shape across versions (see project README history).
 * The class/method names below (`ScalarMultiplication.scalarMult`,
 * `ScalarMultiplication.base`, `Kdf.deriveFromKey`, `SecretBox.easy`, ...)
 * are believed correct for version 0.9.5 based on the library's published
 * docs and mapping convention (`crypto_scalarmult` -> `ScalarMultiplication`,
 * `crypto_kdf_derive_from_key` -> `Kdf.deriveFromKey`, etc.) but have NOT
 * been compiled against the real jar. If the build fails here, this is the
 * only file you should need to touch — check the actual method names in the
 * resolved dependency (Android Studio: Ctrl/Cmd-click the class name) and
 * adjust these four wrapper functions accordingly; DoubleRatchet.kt and
 * everything else only calls through this object.
 */
object CryptoPrimitives {

    const val X25519_KEY_LEN = 32
    const val SECRETBOX_NONCE_LEN = 24
    const val SECRETBOX_KEY_LEN = 32

    /** Random 32-byte X25519 secret scalar (private key half of a DH keypair). */
    fun generateX25519SecretKey(): ByteArray =
        LibsodiumRandom.buf(X25519_KEY_LEN).toByteArray()

    /** Derives the X25519 public key for a given secret scalar. */
    fun x25519PublicFromSecret(secretKey: ByteArray): ByteArray =
        ScalarMultiplication.base(secretKey.toUByteArray()).toByteArray()

    /** Raw Diffie-Hellman: our secret scalar x their public point -> shared secret. */
    fun diffieHellman(ourSecret: ByteArray, theirPublic: ByteArray): ByteArray =
        ScalarMultiplication.scalarMult(ourSecret.toUByteArray(), theirPublic.toUByteArray()).toByteArray()

    /**
     * KDF used for both the root-key ratchet and the symmetric chain ratchet.
     * `subkeyId` lets us derive several independent outputs from the same
     * master key (e.g. 1 = "next chain key", 2 = "message key").
     */
    fun deriveKey(masterKey: ByteArray, subkeyId: Long, context: String, outputLength: Int = 32): ByteArray =
        Kdf.deriveFromKey(
            subkeyId = subkeyId,
            context = context.padEnd(8, '_').take(8),
            masterKey = masterKey.toUByteArray(),
            subkeyLength = outputLength
        ).toByteArray()

    fun randomNonce(): ByteArray = LibsodiumRandom.buf(SECRETBOX_NONCE_LEN).toByteArray()

    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        SecretBox.easy(plaintext.toUByteArray(), nonce.toUByteArray(), key.toUByteArray()).toByteArray()

    /** Throws if [ciphertext] was tampered with or the key/nonce don't match. */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray =
        SecretBox.openEasy(ciphertext.toUByteArray(), nonce.toUByteArray(), key.toUByteArray()).toByteArray()
}
