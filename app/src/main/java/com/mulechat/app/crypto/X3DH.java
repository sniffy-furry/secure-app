package com.mulechat.app.crypto;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * X3DH ("Extended Triple Diffie-Hellman") -- the one-time key agreement two
 * peers who have never spoken before run to reach the first shared secret
 * that DoubleRatchetSession.initAsInitiator/initAsResponder then takes over
 * from. See PreKeyBundle for what a peer publishes, X3DHInitialMessage for
 * what the initiator sends back so the responder can compute the same
 * secret.
 *
 * Identity keys here are X25519, NOT the Ed25519 signing key from
 * identity.Identity -- X25519 (Diffie-Hellman) and Ed25519 (signatures) are
 * different key types on the same curve family, and Tink doesn't expose a
 * safe conversion between them. IdentityGenerator.deriveX25519IdentityKeys
 * derives a companion X25519 identity keypair via HKDF from the same
 * Ed25519 private seed, so it stays fully recoverable from the 24-word
 * phrase -- it's just a separate key, published and pinned separately (see
 * data.PeerContact.x25519IdentityKey).
 *
 * Follows Signal's X3DH spec: SK = HKDF(F || DH1 || DH2 || DH3 || DH4),
 * where F is 32 0xFF bytes (domain separation, standard in the spec) and
 * the DH4 / one-time-prekey term is dropped if the bundle didn't have one
 * to offer. Verified against a reference implementation (Python +
 * `cryptography`'s X25519, independent of this file) that both sides
 * converge on the same secret before this was handed over -- see the
 * project notes for that check.
 */
public final class X3DH {

    private X3DH() {}

    private static final int ONE_TIME_PREKEY_BATCH = 20;

    private static final byte[] F = new byte[32];
    static {
        Arrays.fill(F, (byte) 0xFF);
    }

    public static final class Result {
        public final byte[] sharedSecret;         // 32 bytes -- feed into DoubleRatchetSession.initAsInitiator
        public final byte[] ephemeralPublicKey;    // ours -- put this in the X3DHInitialMessage sent to the responder
        public final Integer usedOneTimePreKeyId;  // null if their bundle had none to offer

        Result(byte[] sharedSecret, byte[] ephemeralPublicKey, Integer usedOneTimePreKeyId) {
            this.sharedSecret = sharedSecret;
            this.ephemeralPublicKey = ephemeralPublicKey;
            this.usedOneTimePreKeyId = usedOneTimePreKeyId;
        }
    }

    /** Thrown when a fetched bundle doesn't check out. Never proceed past this -- it means either a
     *  transport bug or an active attacker substituting keys. */
    public static final class UntrustedBundleException extends GeneralSecurityException {
        public UntrustedBundleException(String message) {
            super(message);
        }
    }

    // ---- Generating our own prekeys (what gets published for others to fetch) ----

    public static SignedPreKey generateSignedPreKey(byte[] ourEd25519PrivateKey, int keyId) throws GeneralSecurityException {
        byte[] secret = CryptoPrimitives.generateX25519SecretKey();
        byte[] publicKey = CryptoPrimitives.x25519PublicFromSecret(secret);
        byte[] signature = CryptoPrimitives.sign(ourEd25519PrivateKey, publicKey);
        return new SignedPreKey(keyId, new X25519KeyPair(secret, publicKey), signature);
    }

    public static List<OneTimePreKey> generateOneTimePreKeys(int count) throws InvalidKeyException {
        List<OneTimePreKey> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] secret = CryptoPrimitives.generateX25519SecretKey();
            byte[] publicKey = CryptoPrimitives.x25519PublicFromSecret(secret);
            keys.add(new OneTimePreKey(-1, new X25519KeyPair(secret, publicKey))); // real keyId assigned on insert
        }
        return keys;
    }

    public static List<OneTimePreKey> generateOneTimePreKeys() throws InvalidKeyException {
        return generateOneTimePreKeys(ONE_TIME_PREKEY_BATCH);
    }

    // ---- Initiator side (Alice: has fetched Bob's PreKeyBundle) ----

    /**
     * @param ourIdentityX25519Secret our own X25519 identity secret (IdentityGenerator.deriveX25519IdentityKeys)
     * @param expectedIdentityKeyEd25519 the peer's Ed25519 identity key as already pinned locally
     *        (data.PeerContact.ed25519PublicKey from when the contact was added/verified) -- checked
     *        against the bundle's claimed identity key before trusting anything else in it. Without
     *        this check a malicious relay could hand out a bundle for a different identity entirely.
     */
    public static Result initiate(byte[] ourIdentityX25519Secret, byte[] expectedIdentityKeyEd25519, PreKeyBundle theirBundle)
            throws GeneralSecurityException {
        if (!Arrays.equals(expectedIdentityKeyEd25519, theirBundle.identityKeyEd25519)) {
            throw new UntrustedBundleException("Bundle identity key does not match the pinned contact");
        }
        if (!CryptoPrimitives.verify(theirBundle.identityKeyEd25519, theirBundle.signedPreKeySignature, theirBundle.signedPreKeyPublic)) {
            throw new UntrustedBundleException("Signed prekey signature does not verify");
        }

        byte[] ephemeralSecret = CryptoPrimitives.generateX25519SecretKey();
        byte[] ephemeralPublic = CryptoPrimitives.x25519PublicFromSecret(ephemeralSecret);

        byte[] dh1 = CryptoPrimitives.diffieHellman(ourIdentityX25519Secret, theirBundle.signedPreKeyPublic);
        byte[] dh2 = CryptoPrimitives.diffieHellman(ephemeralSecret, theirBundle.identityKeyX25519);
        byte[] dh3 = CryptoPrimitives.diffieHellman(ephemeralSecret, theirBundle.signedPreKeyPublic);

        byte[] combined;
        Integer usedOneTimePreKeyId = null;
        if (theirBundle.oneTimePreKeyPublic != null) {
            byte[] dh4 = CryptoPrimitives.diffieHellman(ephemeralSecret, theirBundle.oneTimePreKeyPublic);
            combined = concat(F, dh1, dh2, dh3, dh4);
            usedOneTimePreKeyId = theirBundle.oneTimePreKeyId;
        } else {
            combined = concat(F, dh1, dh2, dh3);
        }

        byte[] sharedSecret = CryptoPrimitives.deriveKey(combined, new byte[32], "nulx3dh", 32);
        return new Result(sharedSecret, ephemeralPublic, usedOneTimePreKeyId);
    }

    // ---- Responder side (Bob: has received an X3DHInitialMessage) ----

    /**
     * @param ourOneTimePreKeySecret pass null if the initial message named no one-time prekey, or if
     *        NulChatRepository.getOneTimePreKeyIfUnused returned null for the id it named (already
     *        consumed / unknown -- e.g. a replayed initial message). The resulting secret still
     *        matches what Alice computed as long as both sides agree on whether a one-time prekey
     *        was used, so a mismatch here (Alice thinks she used one, Bob has none) must be treated
     *        as a failed handshake, not silently downgraded.
     */
    public static byte[] respond(byte[] ourIdentityX25519Secret, byte[] ourSignedPreKeySecret,
                                  byte[] ourOneTimePreKeySecret, byte[] theirIdentityX25519Public,
                                  byte[] theirEphemeralPublic) throws GeneralSecurityException {
        byte[] dh1 = CryptoPrimitives.diffieHellman(ourSignedPreKeySecret, theirIdentityX25519Public);
        byte[] dh2 = CryptoPrimitives.diffieHellman(ourIdentityX25519Secret, theirEphemeralPublic);
        byte[] dh3 = CryptoPrimitives.diffieHellman(ourSignedPreKeySecret, theirEphemeralPublic);

        byte[] combined;
        if (ourOneTimePreKeySecret != null) {
            byte[] dh4 = CryptoPrimitives.diffieHellman(ourOneTimePreKeySecret, theirEphemeralPublic);
            combined = concat(F, dh1, dh2, dh3, dh4);
        } else {
            combined = concat(F, dh1, dh2, dh3);
        }

        return CryptoPrimitives.deriveKey(combined, new byte[32], "nulx3dh", 32);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }
}
