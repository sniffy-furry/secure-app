package com.mulechat.app.identity;

import com.google.crypto.tink.subtle.Ed25519Sign;
import com.mulechat.app.crypto.CryptoPrimitives;
import com.mulechat.app.crypto.X25519KeyPair;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class IdentityGenerator {

    private IdentityGenerator() {}

    /** Creates a brand-new identity with a fresh Ed25519 keypair. */
    public static Identity generate() throws GeneralSecurityException {
        Ed25519Sign.KeyPair keyPair = CryptoPrimitives.generateEd25519KeyPair();
        byte[] publicKey = keyPair.getPublicKey();
        byte[] privateKey = keyPair.getPrivateKey();
        return new Identity(publicKey, privateKey, derivePeerId(publicKey));
    }

    /**
     * Rebuilds an identity from a 32-byte seed. Tink's Ed25519Sign.KeyPair
     * doesn't expose deterministic key generation from a seed directly, so
     * we derive the Ed25519 private key material as HKDF(seed) — meaning
     * recovery from the 24-word phrase reproduces the same key deterministically,
     * without needing Tink to support seeded key generation itself.
     */
    public static Identity fromSeed(byte[] seed) throws GeneralSecurityException {
        // Ed25519 private "keys" in Tink's raw format are 32-byte seeds themselves
        // (the standard EdDSA private key encoding) — deriving one via HKDF from
        // our BIP-39 seed keeps recovery deterministic and simple.
        byte[] ed25519Seed = CryptoPrimitives.deriveKey(seed, new byte[32], "nulchat-identity", 32);
        Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPairFromSeed(ed25519Seed);
        byte[] publicKey = keyPair.getPublicKey();
        byte[] privateKey = keyPair.getPrivateKey();
        return new Identity(publicKey, privateKey, derivePeerId(publicKey));
    }

    /**
     * Derives a companion X25519 keypair for Diffie-Hellman (used by
     * crypto.X3DH), from the same Ed25519 private seed as the signing
     * identity — so it's recoverable from the same 24-word phrase without
     * storing anything extra. This is deliberately a *separate* key from
     * the Ed25519 signing key, not a reinterpretation of it: Ed25519 and
     * X25519 are different key types on the same curve family, and Tink
     * doesn't expose a safe conversion between them.
     */
    public static X25519KeyPair deriveX25519IdentityKeys(Identity identity) throws GeneralSecurityException {
        byte[] secret = CryptoPrimitives.deriveKey(identity.privateKey, new byte[32], "nulchat-x25519-identity", 32);
        byte[] publicKey = CryptoPrimitives.x25519PublicFromSecret(secret);
        return new X25519KeyPair(secret, publicKey);
    }

    /** peerId = base32(sha256(publicKey)[0:12]) — short, shareable, unique. */
    private static String derivePeerId(byte[] publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey);
            byte[] truncated = new byte[12];
            System.arraycopy(hash, 0, truncated, 0, 12);
            return Base32.encode(truncated).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // never happens on Android
        }
    }

    /** Minimal Base32 (RFC 4648, no padding). */
    private static final class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        static String encode(byte[] data) {
            StringBuilder sb = new StringBuilder();
            int bits = 0;
            int value = 0;
            for (byte b : data) {
                value = (value << 8) | (b & 0xFF);
                bits += 8;
                while (bits >= 5) {
                    sb.append(ALPHABET.charAt((value >> (bits - 5)) & 0x1F));
                    bits -= 5;
                }
            }
            if (bits > 0) {
                sb.append(ALPHABET.charAt((value << (5 - bits)) & 0x1F));
            }
            return sb.toString();
        }
    }
}
