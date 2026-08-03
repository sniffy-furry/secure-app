package com.mulechat.app.crypto;

/**
 * Our medium-term X25519 keypair, signed by our Ed25519 identity key so a
 * peer fetching it can be sure it really came from us (see X3DH.initiate,
 * which checks the signature before trusting anything else in a bundle).
 *
 * keyId is a small rotation counter, not a secret -- it just lets an
 * X3DHInitialMessage say "I computed this against your signed prekey #N",
 * so a responder who has since rotated can tell a stale handshake apart
 * from a current one. Rotation itself (regenerating and bumping keyId) is
 * not wired up yet -- NulChatRepository.ensurePreKeysExist only generates
 * one the first time, at identity creation.
 */
public final class SignedPreKey {
    public final int keyId;
    public final X25519KeyPair keyPair;
    public final byte[] signature;

    public SignedPreKey(int keyId, X25519KeyPair keyPair, byte[] signature) {
        this.keyId = keyId;
        this.keyPair = keyPair;
        this.signature = signature;
    }
}
