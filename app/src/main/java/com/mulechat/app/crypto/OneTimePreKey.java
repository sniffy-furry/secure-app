package com.mulechat.app.crypto;

/**
 * A single-use X25519 keypair from our prekey pool. Consuming one in a
 * handshake (X3DH's optional DH4 term) is what gives X3DH real forward
 * secrecy against a compromised signed prekey, not just the identity +
 * signed-prekey terms alone.
 *
 * keyId is -1 for a freshly generated key that hasn't been inserted yet
 * (see X3DH.generateOneTimePreKeys) -- SQLite assigns the real id on
 * insert, via NulChatRepository.saveOneTimePreKeys.
 */
public final class OneTimePreKey {
    public final int keyId;
    public final X25519KeyPair keyPair;

    public OneTimePreKey(int keyId, X25519KeyPair keyPair) {
        this.keyId = keyId;
        this.keyPair = keyPair;
    }
}
