package com.nulchat.data;

import android.content.ContentValues;
import android.database.Cursor;

import com.nulchat.identity.Identity;

import net.sqlcipher.database.SQLiteDatabase;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * All methods here run synchronous SQLite calls — call them from a
 * background thread (e.g. a single-thread ExecutorService), never from the
 * main/UI thread. See ui/ Activities for the pattern used throughout.
 */
public final class NulChatRepository {

    private final SQLiteDatabase db;

    public NulChatRepository(SQLiteDatabase db) {
        this.db = db;
    }

    // ---- Identity ----

    public void saveIdentity(Identity identity, String displayName) {
        ContentValues values = new ContentValues();
        values.put("id", 0);
        values.put("publicKey", identity.publicKey);
        values.put("privateKey", identity.privateKey);
        values.put("peerId", identity.peerId);
        values.put("displayName", displayName);
        values.put("createdAtEpochMs", System.currentTimeMillis());
        db.replace("IdentityRow", null, values);
    }

    public Identity getIdentity() {
        try (Cursor cursor = db.query("IdentityRow", null, "id = 0", null, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            byte[] publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("publicKey"));
            byte[] privateKey = cursor.getBlob(cursor.getColumnIndexOrThrow("privateKey"));
            String peerId = cursor.getString(cursor.getColumnIndexOrThrow("peerId"));
            return new Identity(publicKey, privateKey, peerId);
        }
    }

    // ---- Servers ----

    public String createServer(String name, String ownerPeerId, String description, String iconUri) {
        String id = randomId();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("iconUri", iconUri);
        values.put("description", description == null ? "" : description);
        values.put("ownerPeerId", ownerPeerId);
        values.put("createdAtEpochMs", System.currentTimeMillis());
        db.insert("Server", null, values);
        return id;
    }

    public List<Server> getAllServers() {
        List<Server> result = new ArrayList<>();
        try (Cursor cursor = db.query("Server", null, null, null, null, null, "createdAtEpochMs ASC")) {
            while (cursor.moveToNext()) {
                result.add(new Server(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("iconUri")),
                        cursor.getString(cursor.getColumnIndexOrThrow("description")),
                        cursor.getString(cursor.getColumnIndexOrThrow("ownerPeerId")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMs"))
                ));
            }
        }
        return result;
    }

    // ---- Peers ----

    public void upsertPeerIdentity(String peerId, String displayName, byte[] ed25519PublicKey) {
        PeerContact existing = getPeer(peerId);
        ContentValues values = new ContentValues();
        values.put("peerId", peerId);
        values.put("displayName", displayName);
        values.put("ed25519PublicKey", ed25519PublicKey);
        if (existing != null) {
            values.put("lastKnownHost", existing.lastKnownHost);
            if (existing.lastKnownPort != null) values.put("lastKnownPort", existing.lastKnownPort);
        }
        db.replace("Peer", null, values);
    }

    public PeerContact getPeer(String peerId) {
        try (Cursor cursor = db.query("Peer", null, "peerId = ?", new String[]{peerId}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            return cursorToPeer(cursor);
        }
    }

    public List<PeerContact> getAllPeers() {
        List<PeerContact> result = new ArrayList<>();
        try (Cursor cursor = db.query("Peer", null, null, null, null, null, "displayName ASC")) {
            while (cursor.moveToNext()) {
                result.add(cursorToPeer(cursor));
            }
        }
        return result;
    }

    private PeerContact cursorToPeer(Cursor cursor) {
        int portIndex = cursor.getColumnIndexOrThrow("lastKnownPort");
        Integer port = cursor.isNull(portIndex) ? null : cursor.getInt(portIndex);
        return new PeerContact(
                cursor.getString(cursor.getColumnIndexOrThrow("peerId")),
                cursor.getString(cursor.getColumnIndexOrThrow("displayName")),
                cursor.getBlob(cursor.getColumnIndexOrThrow("ed25519PublicKey")),
                cursor.getString(cursor.getColumnIndexOrThrow("lastKnownHost")),
                port
        );
    }

    public void updatePeerNetworkLocation(String peerId, String host, int port) {
        ContentValues values = new ContentValues();
        values.put("lastKnownHost", host);
        values.put("lastKnownPort", port);
        db.update("Peer", values, "peerId = ?", new String[]{peerId});
    }

    // ---- Messages ----

    public String insertMessage(String peerId, String body, boolean outgoing, String deliveryState) {
        String id = randomId();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("peerId", peerId);
        values.put("body", body);
        values.put("outgoing", outgoing ? 1 : 0);
        values.put("sentAtEpochMs", System.currentTimeMillis());
        values.put("deliveryState", deliveryState);
        db.insert("Message", null, values);
        return id;
    }

    public List<DirectMessage> getMessagesForPeer(String peerId) {
        List<DirectMessage> result = new ArrayList<>();
        try (Cursor cursor = db.query("Message", null, "peerId = ?", new String[]{peerId}, null, null, "sentAtEpochMs ASC")) {
            while (cursor.moveToNext()) {
                result.add(new DirectMessage(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("peerId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("body")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("outgoing")) == 1,
                        cursor.getLong(cursor.getColumnIndexOrThrow("sentAtEpochMs")),
                        cursor.getString(cursor.getColumnIndexOrThrow("deliveryState"))
                ));
            }
        }
        return result;
    }

    private static String randomId() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
