package com.nulchat.crypto;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * See the original Kotlin version's doc comment (kept here for continuity)
 * for the full list of simplifications vs. the real Double Ratchet spec:
 * no X3DH, in-memory-only skipped-key cache, sessions not yet persisted
 * across app restarts. One addition versus the Kotlin version: the message
 * header (sender ratchet public key, previous chain length, message number)
 * is now bound into the AEAD's associated data, so tampering with those
 * fields in transit breaks authentication instead of silently corrupting
 * the ratchet state.
 */
public final class DoubleRatchetSession {

    private byte[] rootKey;
    private byte[] dhSelfSecret;
    private byte[] dhSelfPublic;
    private byte[] dhRemotePublic; // null until the first message is seen
    private byte[] sendChainKey;   // null until this side has ratcheted for sending
    private byte[] recvChainKey;   // null until this side has ratcheted for receiving
    private int sendMessageNumber = 0;
    private int recvMessageNumber = 0;
    private int previousSendChainLength = 0;

    private final Map<String, byte[]> skippedMessageKeys = new HashMap<>();

    private DoubleRatchetSession(byte[] rootKey, byte[] dhSelfSecret, byte[] dhSelfPublic) {
        this.rootKey = rootKey;
        this.dhSelfSecret = dhSelfSecret;
        this.dhSelfPublic = dhSelfPublic;
    }

    public static final class Envelope {
        public final byte[] senderRatchetPublicKey;
        public final int previousChainLength;
        public final int messageNumber;
        public final byte[] ciphertext; // Tink AEAD output — nonce is embedded

        public Envelope(byte[] senderRatchetPublicKey, int previousChainLength, int messageNumber, byte[] ciphertext) {
            this.senderRatchetPublicKey = senderRatchetPublicKey;
            this.previousChainLength = previousChainLength;
            this.messageNumber = messageNumber;
            this.ciphertext = ciphertext;
        }
    }

    public static DoubleRatchetSession initAsInitiator(byte[] sharedSecret, byte[] theirRatchetPublicKey)
            throws GeneralSecurityException {
        byte[] selfSecret = CryptoPrimitives.generateX25519SecretKey();
        byte[] selfPublic = CryptoPrimitives.x25519PublicFromSecret(selfSecret);
        DoubleRatchetSession session = new DoubleRatchetSession(sharedSecret, selfSecret, selfPublic);
        session.dhRatchetStep(theirRatchetPublicKey);
        return session;
    }

    public static DoubleRatchetSession initAsResponder(byte[] sharedSecret, byte[] ourRatchetSecretKey, byte[] ourRatchetPublicKey) {
        return new DoubleRatchetSession(sharedSecret, ourRatchetSecretKey, ourRatchetPublicKey);
    }

    public Envelope encrypt(byte[] plaintext) throws GeneralSecurityException {
        if (sendChainKey == null) throw new IllegalStateException("Session not initialized for sending");
        byte[][] step = symmetricRatchetStep(sendChainKey);
        byte[] messageKey = step[0];
        sendChainKey = step[1];

        int messageNumber = sendMessageNumber;
        byte[] aad = buildAad(dhSelfPublic, previousSendChainLength, messageNumber);
        byte[] ciphertext = CryptoPrimitives.encrypt(messageKey, plaintext, aad);

        Envelope envelope = new Envelope(dhSelfPublic, previousSendChainLength, messageNumber, ciphertext);
        sendMessageNumber += 1;
        return envelope;
    }

    public byte[] decrypt(Envelope envelope) throws GeneralSecurityException {
        String remoteKeyHex = toHex(envelope.senderRatchetPublicKey);
        String skippedKeyId = remoteKeyHex + ":" + envelope.messageNumber;

        byte[] skippedKey = skippedMessageKeys.remove(skippedKeyId);
        if (skippedKey != null) {
            byte[] aad = buildAad(envelope.senderRatchetPublicKey, envelope.previousChainLength, envelope.messageNumber);
            return CryptoPrimitives.decrypt(skippedKey, envelope.ciphertext, aad);
        }

        if (dhRemotePublic == null || !java.util.Arrays.equals(envelope.senderRatchetPublicKey, dhRemotePublic)) {
            skipRecvMessageKeys(envelope.previousChainLength);
            dhRatchetStep(envelope.senderRatchetPublicKey);
        }

        skipRecvMessageKeys(envelope.messageNumber);

        if (recvChainKey == null) throw new IllegalStateException("Session not initialized for receiving");
        byte[][] step = symmetricRatchetStep(recvChainKey);
        byte[] messageKey = step[0];
        recvChainKey = step[1];
        recvMessageNumber += 1;

        byte[] aad = buildAad(envelope.senderRatchetPublicKey, envelope.previousChainLength, envelope.messageNumber);
        return CryptoPrimitives.decrypt(messageKey, envelope.ciphertext, aad);
    }

    private void skipRecvMessageKeys(int until) throws GeneralSecurityException {
        if (recvChainKey == null || dhRemotePublic == null) return;
        byte[] current = recvChainKey;
        int n = recvMessageNumber;
        String remoteKeyHex = toHex(dhRemotePublic);
        while (n < until) {
            byte[][] step = symmetricRatchetStep(current);
            skippedMessageKeys.put(remoteKeyHex + ":" + n, step[0]);
            current = step[1];
            n += 1;
        }
        recvChainKey = current;
        recvMessageNumber = n;
    }

    private void dhRatchetStep(byte[] newRemotePublic) throws GeneralSecurityException {
        previousSendChainLength = sendMessageNumber;
        sendMessageNumber = 0;
        recvMessageNumber = 0;
        dhRemotePublic = newRemotePublic;

        byte[] recvShared = CryptoPrimitives.diffieHellman(dhSelfSecret, newRemotePublic);
        byte[][] recvStep = rootKdfStep(rootKey, recvShared);
        rootKey = recvStep[0];
        recvChainKey = recvStep[1];

        dhSelfSecret = CryptoPrimitives.generateX25519SecretKey();
        dhSelfPublic = CryptoPrimitives.x25519PublicFromSecret(dhSelfSecret);
        byte[] sendShared = CryptoPrimitives.diffieHellman(dhSelfSecret, newRemotePublic);
        byte[][] sendStep = rootKdfStep(rootKey, sendShared);
        rootKey = sendStep[0];
        sendChainKey = sendStep[1];
    }

    private byte[][] rootKdfStep(byte[] currentRootKey, byte[] dhOutput) throws GeneralSecurityException {
        byte[] nextRootKey = CryptoPrimitives.deriveKey(dhOutput, currentRootKey, "nulroot", 32);
        byte[] chainKey = CryptoPrimitives.deriveKey(dhOutput, currentRootKey, "nulchain", 32);
        return new byte[][] { nextRootKey, chainKey };
    }

    private byte[][] symmetricRatchetStep(byte[] chainKey) throws GeneralSecurityException {
        byte[] messageKey = CryptoPrimitives.deriveKey(chainKey, new byte[32], "nulmsg", 32);
        byte[] nextChainKey = CryptoPrimitives.deriveKey(chainKey, new byte[32], "nulchn", 32);
        return new byte[][] { messageKey, nextChainKey };
    }

    private static byte[] buildAad(byte[] senderRatchetPublicKey, int previousChainLength, int messageNumber) {
        byte[] aad = new byte[senderRatchetPublicKey.length + 8];
        System.arraycopy(senderRatchetPublicKey, 0, aad, 0, senderRatchetPublicKey.length);
        int offset = senderRatchetPublicKey.length;
        aad[offset] = (byte) (previousChainLength >> 24);
        aad[offset + 1] = (byte) (previousChainLength >> 16);
        aad[offset + 2] = (byte) (previousChainLength >> 8);
        aad[offset + 3] = (byte) previousChainLength;
        aad[offset + 4] = (byte) (messageNumber >> 24);
        aad[offset + 5] = (byte) (messageNumber >> 16);
        aad[offset + 6] = (byte) (messageNumber >> 8);
        aad[offset + 7] = (byte) messageNumber;
        return aad;
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
