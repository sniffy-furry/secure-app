package com.mulechat.app.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * See the original Kotlin version's doc comment (kept here for continuity)
 * for the full list of simplifications vs. the real Double Ratchet spec:
 * no X3DH -- see the class's doc comment further down for what that means
 * in practice. One addition versus the Kotlin version: the message
 * header (sender ratchet public key, previous chain length, message number)
 * is now bound into the AEAD's associated data, so tampering with those
 * fields in transit breaks authentication instead of silently corrupting
 * the ratchet state. A second addition (this pass): {@link #serialize()} /
 * {@link #deserialize(byte[])} let a session survive an app restart --
 * see storage.NulChatRepository for where this gets persisted.
 *
 * Still missing, deliberately, for a later pass:
 *  - No X3DH. This class only ever ratchets an *existing* shared secret --
 *    it has no opinion on how two peers who've never spoken before agree on
 *    that first secret. Wiring that up needs a signed-prekey bundle
 *    (published somewhere reachable, e.g. via the relay) and an initial
 *    key-agreement step run once per new contact, before the first
 *    {@link #initAsInitiator}/{@link #initAsResponder} call.
 *  - The skipped-message-key cache is persisted now, but only up to
 *    whatever's in memory at the moment {@link #serialize()} is called --
 *    call it after every decrypt, not just on a clean app exit, or a
 *    crash between messages can still lose a skipped key.
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

    // ---- Persistence -----------------------------------------------------
    //
    // Plain length-prefixed binary, not any standard format -- this is an
    // internal detail of this app, never sent over the wire. Store the
    // resulting bytes encrypted (the DB they land in via NulChatRepository
    // is already SQLCipher-encrypted at rest, so no separate encryption is
    // applied here on top).

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);

        writeField(out, rootKey);
        writeField(out, dhSelfSecret);
        writeField(out, dhSelfPublic);
        writeField(out, dhRemotePublic);
        writeField(out, sendChainKey);
        writeField(out, recvChainKey);
        out.writeInt(sendMessageNumber);
        out.writeInt(recvMessageNumber);
        out.writeInt(previousSendChainLength);

        out.writeInt(skippedMessageKeys.size());
        for (Map.Entry<String, byte[]> entry : skippedMessageKeys.entrySet()) {
            writeField(out, entry.getKey().getBytes("UTF-8"));
            writeField(out, entry.getValue());
        }

        out.flush();
        return bytes.toByteArray();
    }

    public static DoubleRatchetSession deserialize(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        byte[] rootKey = readField(in);
        byte[] dhSelfSecret = readField(in);
        byte[] dhSelfPublic = readField(in);
        byte[] dhRemotePublic = readField(in);
        byte[] sendChainKey = readField(in);
        byte[] recvChainKey = readField(in);
        int sendMessageNumber = in.readInt();
        int recvMessageNumber = in.readInt();
        int previousSendChainLength = in.readInt();

        DoubleRatchetSession session = new DoubleRatchetSession(rootKey, dhSelfSecret, dhSelfPublic);
        session.dhRemotePublic = dhRemotePublic;
        session.sendChainKey = sendChainKey;
        session.recvChainKey = recvChainKey;
        session.sendMessageNumber = sendMessageNumber;
        session.recvMessageNumber = recvMessageNumber;
        session.previousSendChainLength = previousSendChainLength;

        int skippedCount = in.readInt();
        for (int i = 0; i < skippedCount; i++) {
            String key = new String(readField(in), "UTF-8");
            byte[] value = readField(in);
            session.skippedMessageKeys.put(key, value);
        }

        return session;
    }

    private static void writeField(DataOutputStream out, byte[] field) throws IOException {
        if (field == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(field.length);
            out.write(field);
        }
    }

    private static byte[] readField(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) return null;
        byte[] field = new byte[length];
        in.readFully(field);
        return field;
    }
}
