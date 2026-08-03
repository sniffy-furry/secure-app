package com.mulechat.app.crypto;

import com.mulechat.app.identity.Identity;
import com.mulechat.app.identity.IdentityGenerator;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 * Runs a full X3DH handshake between two locally-generated identities
 * (Alice + Bob), entirely in-process, on the real crypto stack (Tink) this
 * app ships with -- no network, no UI wiring beyond the debug button in
 * SettingsFragment, nothing persisted to the real DB. Exists purely to
 * answer "does X3DH actually work on this device" before building the
 * transport layer that would let two real phones run it against each
 * other.
 *
 * Checks, in order: a handshake using a one-time prekey, one without
 * (simulating an exhausted pool), that a bundle with a forged signature is
 * rejected, and that a bundle claiming the wrong pinned identity is
 * rejected. Throws GeneralSecurityException with a description of whatever
 * failed first; returns a short human-readable report on success.
 *
 * Delete this class (and its button in SettingsFragment/fragment_settings)
 * once the transport layer exists and real two-device testing replaces it.
 */
public final class X3DHSelfTest {

    private X3DHSelfTest() {}

    public static String run() throws GeneralSecurityException {
        StringBuilder report = new StringBuilder();

        Identity alice = IdentityGenerator.generate();
        Identity bob = IdentityGenerator.generate();
        X25519KeyPair aliceIdentityX25519 = IdentityGenerator.deriveX25519IdentityKeys(alice);
        X25519KeyPair bobIdentityX25519 = IdentityGenerator.deriveX25519IdentityKeys(bob);

        SignedPreKey bobSignedPreKey = X3DH.generateSignedPreKey(bob.privateKey, 1);
        List<OneTimePreKey> bobOneTimePreKeys = X3DH.generateOneTimePreKeys(1);
        OneTimePreKey bobOtpk = bobOneTimePreKeys.get(0);

        PreKeyBundle bobBundle = new PreKeyBundle(
                bob.peerId, bob.publicKey, bobIdentityX25519.publicKey,
                bobSignedPreKey.keyId, bobSignedPreKey.keyPair.publicKey, bobSignedPreKey.signature,
                bobOtpk.keyId, bobOtpk.keyPair.publicKey);

        // --- 1. Handshake with a one-time prekey ---
        X3DH.Result aliceResult = X3DH.initiate(aliceIdentityX25519.secretKey, bob.publicKey, bobBundle);
        byte[] bobSecret = X3DH.respond(
                bobIdentityX25519.secretKey, bobSignedPreKey.keyPair.secretKey, bobOtpk.keyPair.secretKey,
                aliceIdentityX25519.publicKey, aliceResult.ephemeralPublicKey);

        boolean matchWithOtpk = Arrays.equals(aliceResult.sharedSecret, bobSecret);
        report.append("With one-time prekey: ").append(matchWithOtpk ? "MATCH" : "MISMATCH").append('\n');
        if (!matchWithOtpk) {
            throw new GeneralSecurityException("Shared secrets do not match (with one-time prekey). Alice="
                    + toHex(aliceResult.sharedSecret) + " Bob=" + toHex(bobSecret));
        }

        // --- 2. Handshake without one-time prekey (pool exhausted) ---
        PreKeyBundle bobBundleNoOtpk = new PreKeyBundle(
                bob.peerId, bob.publicKey, bobIdentityX25519.publicKey,
                bobSignedPreKey.keyId, bobSignedPreKey.keyPair.publicKey, bobSignedPreKey.signature,
                null, null);
        X3DH.Result aliceResult2 = X3DH.initiate(aliceIdentityX25519.secretKey, bob.publicKey, bobBundleNoOtpk);
        byte[] bobSecret2 = X3DH.respond(
                bobIdentityX25519.secretKey, bobSignedPreKey.keyPair.secretKey, null,
                aliceIdentityX25519.publicKey, aliceResult2.ephemeralPublicKey);

        boolean matchNoOtpk = Arrays.equals(aliceResult2.sharedSecret, bobSecret2);
        report.append("Without one-time prekey: ").append(matchNoOtpk ? "MATCH" : "MISMATCH").append('\n');
        if (!matchNoOtpk) {
            throw new GeneralSecurityException("Shared secrets do not match (without one-time prekey).");
        }

        // --- 3. A forged signature must be rejected ---
        Identity mallory = IdentityGenerator.generate();
        boolean rejectedForgedSignature = false;
        try {
            PreKeyBundle forgedBundle = new PreKeyBundle(
                    bob.peerId, bob.publicKey, bobIdentityX25519.publicKey,
                    bobSignedPreKey.keyId, bobSignedPreKey.keyPair.publicKey,
                    CryptoPrimitives.sign(mallory.privateKey, bobSignedPreKey.keyPair.publicKey), // wrong signer
                    null, null);
            X3DH.initiate(aliceIdentityX25519.secretKey, bob.publicKey, forgedBundle);
        } catch (X3DH.UntrustedBundleException expected) {
            rejectedForgedSignature = true;
        }
        report.append("Rejects forged signature: ").append(rejectedForgedSignature ? "YES" : "NO (BUG!)").append('\n');
        if (!rejectedForgedSignature) {
            throw new GeneralSecurityException("A bundle with a signature from the wrong identity was NOT rejected");
        }

        // --- 4. A bundle claiming the wrong pinned identity must be rejected ---
        boolean rejectedWrongIdentity = false;
        try {
            X3DH.initiate(aliceIdentityX25519.secretKey, mallory.publicKey /* wrong expected identity */, bobBundle);
        } catch (X3DH.UntrustedBundleException expected) {
            rejectedWrongIdentity = true;
        }
        report.append("Rejects identity mismatch: ").append(rejectedWrongIdentity ? "YES" : "NO (BUG!)").append('\n');
        if (!rejectedWrongIdentity) {
            throw new GeneralSecurityException("A bundle for the wrong pinned identity was NOT rejected");
        }

        report.append("\nAll checks passed.");
        return report.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
