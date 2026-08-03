package com.mulechat.app.crypto;

/**
 * A generic Curve25519 (Diffie-Hellman) keypair. Used for three different
 * roles in X3DH -- the identity DH key, the medium-term signed prekey, and
 * the single-use one-time prekeys -- which is why this is a standalone
 * class instead of being folded into any one of them.
 */
public final class X25519KeyPair {
    public final byte[] secretKey;
    public final byte[] publicKey;

    public X25519KeyPair(byte[] secretKey, byte[] publicKey) {
        this.secretKey = secretKey;
        this.publicKey = publicKey;
    }
}
