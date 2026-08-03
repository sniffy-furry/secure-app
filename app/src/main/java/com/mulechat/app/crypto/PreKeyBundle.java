package com.mulechat.app.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What a peer publishes so someone who has never talked to them before can
 * start a session (see X3DH.initiate). Everything in here is public -- no
 * secret key material ever leaves the device that generated it.
 *
 * Where this actually gets published (a directory service reachable via
 * the relay, a QR code exchanged in person, etc.) is a transport-layer
 * decision and deliberately not made here -- this class only defines the
 * bytes that would travel, via serialize()/deserialize(), the same way
 * DoubleRatchetSession.Envelope does for ratchet messages.
 */
public final class PreKeyBundle {
    public final String peerId;
    public final byte[] identityKeyEd25519;    // == identity.Identity.publicKey -- for pinning + verifying the signature below
    public final byte[] identityKeyX25519;     // == IdentityGenerator.deriveX25519IdentityKeys(identity).publicKey
    public final int signedPreKeyId;
    public final byte[] signedPreKeyPublic;
    public final byte[] signedPreKeySignature; // Ed25519 signature by identityKeyEd25519 over signedPreKeyPublic
    public final Integer oneTimePreKeyId;      // null if the pool was empty when this bundle was built
    public final byte[] oneTimePreKeyPublic;   // null iff oneTimePreKeyId is null

    public PreKeyBundle(String peerId, byte[] identityKeyEd25519, byte[] identityKeyX25519,
                         int signedPreKeyId, byte[] signedPreKeyPublic, byte[] signedPreKeySignature,
                         Integer oneTimePreKeyId, byte[] oneTimePreKeyPublic) {
        this.peerId = peerId;
        this.identityKeyEd25519 = identityKeyEd25519;
        this.identityKeyX25519 = identityKeyX25519;
        this.signedPreKeyId = signedPreKeyId;
        this.signedPreKeyPublic = signedPreKeyPublic;
        this.signedPreKeySignature = signedPreKeySignature;
        this.oneTimePreKeyId = oneTimePreKeyId;
        this.oneTimePreKeyPublic = oneTimePreKeyPublic;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);

        out.writeUTF(peerId);
        writeField(out, identityKeyEd25519);
        writeField(out, identityKeyX25519);
        out.writeInt(signedPreKeyId);
        writeField(out, signedPreKeyPublic);
        writeField(out, signedPreKeySignature);
        out.writeBoolean(oneTimePreKeyId != null);
        if (oneTimePreKeyId != null) {
            out.writeInt(oneTimePreKeyId);
            writeField(out, oneTimePreKeyPublic);
        }

        out.flush();
        return bytes.toByteArray();
    }

    public static PreKeyBundle deserialize(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        String peerId = in.readUTF();
        byte[] identityKeyEd25519 = readField(in);
        byte[] identityKeyX25519 = readField(in);
        int signedPreKeyId = in.readInt();
        byte[] signedPreKeyPublic = readField(in);
        byte[] signedPreKeySignature = readField(in);

        Integer oneTimePreKeyId = null;
        byte[] oneTimePreKeyPublic = null;
        if (in.readBoolean()) {
            oneTimePreKeyId = in.readInt();
            oneTimePreKeyPublic = readField(in);
        }

        return new PreKeyBundle(peerId, identityKeyEd25519, identityKeyX25519, signedPreKeyId,
                signedPreKeyPublic, signedPreKeySignature, oneTimePreKeyId, oneTimePreKeyPublic);
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
