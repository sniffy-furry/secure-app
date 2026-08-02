package com.nulchat.crypto;

import com.google.crypto.tink.subtle.ChaCha20Poly1305;
import com.google.crypto.tink.subtle.Ed25519Sign;
import com.google.crypto.tink.subtle.Ed25519Verify;
import com.google.crypto.tink.subtle.Hkdf;
import com.google.crypto.tink.subtle.Random;
import com.google.crypto.tink.subtle.X25519;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

/**
 * Every direct call into Tink lives here and nowhere else, so if a method
 * signature ever changes between Tink versions, this is the one file to fix.
 *
 * API confirmed against Tink's published source/javadoc (X25519, Hkdf) as of
 * writing — see the project README for the exact classes/signatures checked.
 * The X25519 class is marked {@code @Alpha} by Tink itself (their internal
 * "not yet promoted to a stable API" marker) — it's still the standard way
 * to get raw X25519 out of Tink for a custom protocol like this one, and is
 * used the same way by other production libraries (e.g. nimbus-jose-jwt),
 * but it's honest to flag that Tink itself doesn't call it stable.
 */
public final class CryptoPrimitives {

    private CryptoPrimitives() {}

    public static final int X25519_KEY_LEN = 32;

    // ---- X25519 (Diffie-Hellman for the ratchet) ----

    public static byte[] generateX25519SecretKey() {
        return X25519.generatePrivateKey();
    }

    public static byte[] x25519PublicFromSecret(byte[] secretKey) throws InvalidKeyException {
        return X25519.publicFromPrivate(secretKey);
    }

    public static byte[] diffieHellman(byte[] ourSecret, byte[] theirPublic) throws InvalidKeyException {
        return X25519.computeSharedSecret(ourSecret, theirPublic);
    }

    // ---- Ed25519 (identity signing — proves you own your peerId) ----

    public static Ed25519Sign.KeyPair generateEd25519KeyPair() throws GeneralSecurityException {
        return Ed25519Sign.KeyPair.newKeyPair();
    }

    public static byte[] sign(byte[] ed25519PrivateKey, byte[] data) throws GeneralSecurityException {
        return new Ed25519Sign(ed25519PrivateKey).sign(data);
    }

    public static boolean verify(byte[] ed25519PublicKey, byte[] signature, byte[] data) {
        try {
            new Ed25519Verify(ed25519PublicKey).verify(signature, data);
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // ---- HKDF (used for every ratchet step: root KDF + symmetric chain KDF) ----

    /**
     * @param context a short ASCII label distinguishing what's being derived
     *                (e.g. "nulroot", "nulmsg") — becomes HKDF's "info" parameter.
     */
    public static byte[] deriveKey(byte[] inputKeyMaterial, byte[] salt, String context, int outputLength)
            throws GeneralSecurityException {
        return Hkdf.computeHkdf(
                "HMACSHA256",
                inputKeyMaterial,
                salt,
                context.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                outputLength
        );
    }

    // ---- AEAD (per-message encryption) ----
    // Tink's ChaCha20Poly1305 generates and prepends its own nonce to the
    // output, and strips it again on decrypt — no separate nonce field needed
    // in our wire format.

    public static byte[] encrypt(byte[] key, byte[] plaintext, byte[] associatedData) throws GeneralSecurityException {
        return new ChaCha20Poly1305(key).encrypt(plaintext, associatedData);
    }

    public static byte[] decrypt(byte[] key, byte[] ciphertext, byte[] associatedData) throws GeneralSecurityException {
        return new ChaCha20Poly1305(key).decrypt(ciphertext, associatedData);
    }

    public static byte[] randomBytes(int length) {
        return Random.randBytes(length);
    }
}
