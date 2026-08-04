package com.mulechat.app.backup;

import com.mulechat.app.crypto.CryptoPrimitives;
import com.mulechat.app.data.DirectMessage;
import com.mulechat.app.data.NulChatRepository;
import com.mulechat.app.data.PeerContact;
import com.mulechat.app.identity.Identity;
import com.mulechat.app.identity.IdentityGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and restores encrypted local backups of contacts + message
 * history. Deliberately does NOT include identity keys, ratchet session
 * state, or prekeys -- the identity is already recoverable from the
 * 24-word phrase, and duplicating live session/prekey secrets into a file
 * that's meant to sit in a synced folder would undermine the Double
 * Ratchet's forward secrecy for no real benefit (a restored device starts
 * fresh sessions with each peer regardless).
 *
 * The backup key itself comes from IdentityGenerator.deriveBackupKey --
 * deterministic from the same seed phrase, so there's no separate backup
 * passphrase to manage. Encrypted with CryptoPrimitives.encrypt (AEAD);
 * the identity's peerId is bound in as associated data so a backup file
 * can't silently be decrypted as if it belonged to a different identity.
 *
 * Pure logic, no file I/O and no android.* imports -- callers (see
 * backup.BackupWorker) own reading/writing the actual bytes via SAF.
 */
public final class BackupExporter {

    private static final int FORMAT_VERSION = 1;

    private BackupExporter() {}

    public static byte[] buildEncryptedBackup(NulChatRepository repo, Identity identity) throws IOException, GeneralSecurityException {
        byte[] plaintext = serialize(repo);
        byte[] key = IdentityGenerator.deriveBackupKey(identity);
        return CryptoPrimitives.encrypt(key, plaintext, associatedData(identity));
    }

    public static void restoreEncryptedBackup(byte[] encrypted, NulChatRepository repo, Identity identity)
            throws IOException, GeneralSecurityException {
        byte[] key = IdentityGenerator.deriveBackupKey(identity);
        byte[] plaintext = CryptoPrimitives.decrypt(key, encrypted, associatedData(identity));
        deserializeInto(plaintext, repo);
    }

    private static byte[] associatedData(Identity identity) {
        return identity.peerId.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] serialize(NulChatRepository repo) throws IOException {
        List<PeerContact> peers = repo.getAllPeers();
        List<DirectMessage> messages = repo.getAllMessages();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);

        out.writeInt(FORMAT_VERSION);
        out.writeLong(System.currentTimeMillis());

        out.writeInt(peers.size());
        for (PeerContact peer : peers) {
            out.writeUTF(peer.peerId);
            out.writeUTF(peer.displayName);
            writeField(out, peer.ed25519PublicKey);
            out.writeBoolean(peer.x25519IdentityKey != null);
            if (peer.x25519IdentityKey != null) writeField(out, peer.x25519IdentityKey);
            out.writeBoolean(peer.lastKnownHost != null);
            if (peer.lastKnownHost != null) out.writeUTF(peer.lastKnownHost);
            out.writeBoolean(peer.lastKnownPort != null);
            if (peer.lastKnownPort != null) out.writeInt(peer.lastKnownPort);
        }

        out.writeInt(messages.size());
        for (DirectMessage message : messages) {
            out.writeUTF(message.id);
            out.writeUTF(message.peerId);
            out.writeUTF(message.body);
            out.writeBoolean(message.outgoing);
            out.writeLong(message.sentAtEpochMs);
            out.writeUTF(message.deliveryState);
        }

        out.flush();
        return bytes.toByteArray();
    }

    private static void deserializeInto(byte[] data, NulChatRepository repo) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported backup format version: " + version);
        }
        in.readLong(); // exportedAtEpochMs -- informational only, not needed to restore

        int peerCount = in.readInt();
        List<PeerContact> peers = new ArrayList<>(peerCount);
        for (int i = 0; i < peerCount; i++) {
            String peerId = in.readUTF();
            String displayName = in.readUTF();
            byte[] ed25519PublicKey = readField(in);
            byte[] x25519IdentityKey = in.readBoolean() ? readField(in) : null;
            String lastKnownHost = in.readBoolean() ? in.readUTF() : null;
            Integer lastKnownPort = in.readBoolean() ? in.readInt() : null;
            peers.add(new PeerContact(peerId, displayName, ed25519PublicKey, x25519IdentityKey, lastKnownHost, lastKnownPort));
        }

        int messageCount = in.readInt();
        List<DirectMessage> messages = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            String id = in.readUTF();
            String peerId = in.readUTF();
            String body = in.readUTF();
            boolean outgoing = in.readBoolean();
            long sentAtEpochMs = in.readLong();
            String deliveryState = in.readUTF();
            messages.add(new DirectMessage(id, peerId, body, outgoing, sentAtEpochMs, deliveryState));
        }

        // Peers first -- Message.peerId has a foreign key into Peer.
        for (PeerContact peer : peers) repo.restorePeer(peer);
        for (DirectMessage message : messages) repo.restoreMessageIfAbsent(message);
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
