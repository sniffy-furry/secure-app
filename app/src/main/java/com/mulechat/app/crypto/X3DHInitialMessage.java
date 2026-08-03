package com.mulechat.app.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What the initiator (Alice) sends the responder (Bob) so he can compute
 * the same X3DH shared secret and set up his side of the ratchet via
 * DoubleRatchetSession.initAsResponder(sharedSecret, ourSignedPreKeySecret,
 * ourSignedPreKeyPublic). Carries no secret material of Alice's -- just
 * enough public info, plus which of Bob's published keys it was computed
 * against.
 *
 * In the finished protocol this would ride alongside (or as the header of)
 * Alice's first DoubleRatchetSession.Envelope. How it actually reaches Bob
 * is a transport-layer decision, same as PreKeyBundle -- not made here.
 */
public final class X3DHInitialMessage {
    public final byte[] identityKeyX25519;  // Alice's, from IdentityGenerator.deriveX25519IdentityKeys
    public final byte[] ephemeralPublicKey; // Alice's, from X3DH.Result.ephemeralPublicKey
    public final int signedPreKeyId;        // which of Bob's signed prekeys this was computed against
    public final Integer oneTimePreKeyId;   // which of Bob's one-time prekeys was consumed, or null if his bundle had none

    public X3DHInitialMessage(byte[] identityKeyX25519, byte[] ephemeralPublicKey, int signedPreKeyId, Integer oneTimePreKeyId) {
        this.identityKeyX25519 = identityKeyX25519;
        this.ephemeralPublicKey = ephemeralPublicKey;
        this.signedPreKeyId = signedPreKeyId;
        this.oneTimePreKeyId = oneTimePreKeyId;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);

        writeField(out, identityKeyX25519);
        writeField(out, ephemeralPublicKey);
        out.writeInt(signedPreKeyId);
        out.writeBoolean(oneTimePreKeyId != null);
        if (oneTimePreKeyId != null) out.writeInt(oneTimePreKeyId);

        out.flush();
        return bytes.toByteArray();
    }

    public static X3DHInitialMessage deserialize(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        byte[] identityKeyX25519 = readField(in);
        byte[] ephemeralPublicKey = readField(in);
        int signedPreKeyId = in.readInt();
        Integer oneTimePreKeyId = in.readBoolean() ? in.readInt() : null;

        return new X3DHInitialMessage(identityKeyX25519, ephemeralPublicKey, signedPreKeyId, oneTimePreKeyId);
    }

    private static void writeField(DataOutputStream out, byte[] field) throws IOException {
        out.writeInt(field.length);
        out.write(field);
    }

    private static byte[] readField(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] field = new byte[length];
        in.readFully(field);
        return field;
    }
}
