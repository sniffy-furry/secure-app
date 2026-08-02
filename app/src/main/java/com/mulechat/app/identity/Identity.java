package com.mulechat.app.identity;

import java.util.Arrays;

/**
 * A user's self-sovereign cryptographic identity (Ed25519 keypair + a
 * short peerId derived from the public key). The private key never leaves
 * the device unencrypted — see storage/ for how it's persisted.
 */
public final class Identity {
    public final byte[] publicKey;
    public final byte[] privateKey;
    public final String peerId;

    public Identity(byte[] publicKey, byte[] privateKey, String peerId) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.peerId = peerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Identity)) return false;
        Identity other = (Identity) o;
        return Arrays.equals(publicKey, other.publicKey)
                && Arrays.equals(privateKey, other.privateKey)
                && peerId.equals(other.peerId);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(publicKey);
        result = 31 * result + Arrays.hashCode(privateKey);
        result = 31 * result + peerId.hashCode();
        return result;
    }
}
