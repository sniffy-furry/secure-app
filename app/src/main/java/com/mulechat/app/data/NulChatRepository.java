package com.mulechat.app.data;

import android.content.ContentValues;
import android.database.Cursor;

import com.mulechat.app.crypto.OneTimePreKey;
import com.mulechat.app.crypto.PreKeyBundle;
import com.mulechat.app.crypto.SignedPreKey;
import com.mulechat.app.crypto.X25519KeyPair;
import com.mulechat.app.crypto.X3DH;
import com.mulechat.app.identity.Identity;

import net.sqlcipher.database.SQLiteDatabase;

import java.security.GeneralSecurityException;
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
            if (existing.x25519IdentityKey != null) values.put("x25519IdentityKey", existing.x25519IdentityKey);
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
        int x25519Index = cursor.getColumnIndexOrThrow("x25519IdentityKey");
        byte[] x25519IdentityKey = cursor.isNull(x25519Index) ? null : cursor.getBlob(x25519Index);
        return new PeerContact(
                cursor.getString(cursor.getColumnIndexOrThrow("peerId")),
                cursor.getString(cursor.getColumnIndexOrThrow("displayName")),
                cursor.getBlob(cursor.getColumnIndexOrThrow("ed25519PublicKey")),
                x25519IdentityKey,
                cursor.getString(cursor.getColumnIndexOrThrow("lastKnownHost")),
                port
        );
    }

    /** Pins a peer's X3DH identity key the first time we see it (in their first PreKeyBundle). */
    public void pinPeerX25519IdentityKey(String peerId, byte[] x25519IdentityKey) {
        ContentValues values = new ContentValues();
        values.put("x25519IdentityKey", x25519IdentityKey);
        db.update("Peer", values, "peerId = ?", new String[]{peerId});
    }

    public void updatePeerNetworkLocation(String peerId, String host, int port) {
        ContentValues values = new ContentValues();
        values.put("lastKnownHost", host);
        values.put("lastKnownPort", port);
        db.update("Peer", values, "peerId = ?", new String[]{peerId});
    }

    // ---- Ratchet sessions ----
    // Stores the serialized DoubleRatchetSession state per peer, so a
    // conversation's ratchet survives an app restart. Call saveRatchetSession
    // after every encrypt() and decrypt() call on a session -- not just on
    // clean app exit -- or a crash between messages can still lose state
    // (see DoubleRatchetSession's doc comment).

    public void saveRatchetSession(String peerId, byte[] serializedSession) {
        ContentValues values = new ContentValues();
        values.put("peerId", peerId);
        values.put("sessionBlob", serializedSession);
        values.put("updatedAtEpochMs", System.currentTimeMillis());
        db.replace("RatchetSession", null, values);
    }

    /** Returns null if no session has been saved yet for this peer. */
    public byte[] loadRatchetSession(String peerId) {
        try (Cursor cursor = db.query("RatchetSession", null, "peerId = ?", new String[]{peerId}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            return cursor.getBlob(cursor.getColumnIndexOrThrow("sessionBlob"));
        }
    }

    public void deleteRatchetSession(String peerId) {
        db.delete("RatchetSession", "peerId = ?", new String[]{peerId});
    }

    // ---- X3DH prekeys ----
    //
    // Everything a peer needs to start a session with us without either of
    // us being online at the same time -- see crypto.X3DH. The signed
    // prekey is one row (id = 0, replace on rotation, same pattern as
    // IdentityRow); the one-time prekey pool is a table of single-use rows
    // consumed as bundles go out. Neither rotation nor pool replenishment
    // is wired up yet -- ensurePreKeysExist only ever generates once, right
    // after identity creation.

    private static final int ONE_TIME_PREKEY_BATCH = 20;

    /**
     * Generates and stores our signed prekey plus a batch of one-time
     * prekeys if we don't have any yet. Call once, right after
     * saveIdentity() (see OnboardingActivity / RecoveryPhraseActivity).
     * Safe to call again later -- does nothing if a signed prekey already
     * exists.
     */
    public void ensurePreKeysExist(Identity identity) throws GeneralSecurityException {
        if (getSignedPreKey() != null) return;
        SignedPreKey signedPreKey = X3DH.generateSignedPreKey(identity.privateKey, 1);
        saveSignedPreKey(signedPreKey);
        saveOneTimePreKeys(X3DH.generateOneTimePreKeys(ONE_TIME_PREKEY_BATCH));
    }

    public void saveSignedPreKey(SignedPreKey signedPreKey) {
        ContentValues values = new ContentValues();
        values.put("id", 0);
        values.put("keyId", signedPreKey.keyId);
        values.put("publicKey", signedPreKey.keyPair.publicKey);
        values.put("privateKey", signedPreKey.keyPair.secretKey);
        values.put("signature", signedPreKey.signature);
        values.put("createdAtEpochMs", System.currentTimeMillis());
        db.replace("SignedPreKey", null, values);
    }

    /** Returns null if ensurePreKeysExist() hasn't run yet. */
    public SignedPreKey getSignedPreKey() {
        try (Cursor cursor = db.query("SignedPreKey", null, "id = 0", null, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            int keyId = cursor.getInt(cursor.getColumnIndexOrThrow("keyId"));
            byte[] publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("publicKey"));
            byte[] privateKey = cursor.getBlob(cursor.getColumnIndexOrThrow("privateKey"));
            byte[] signature = cursor.getBlob(cursor.getColumnIndexOrThrow("signature"));
            return new SignedPreKey(keyId, new X25519KeyPair(privateKey, publicKey), signature);
        }
    }

    public void saveOneTimePreKeys(List<OneTimePreKey> keys) {
        db.beginTransaction();
        try {
            for (OneTimePreKey key : keys) {
                ContentValues values = new ContentValues();
                values.put("publicKey", key.keyPair.publicKey);
                values.put("privateKey", key.keyPair.secretKey);
                values.put("used", 0);
                values.put("createdAtEpochMs", System.currentTimeMillis());
                db.insert("OneTimePreKey", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int countUnusedOneTimePreKeys() {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM OneTimePreKey WHERE used = 0", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    /**
     * Hands out one unused one-time prekey for a bundle we're about to
     * publish, marking it used immediately -- a bundle is meant to be
     * fetched once. Returns null if the pool is empty (X3DH still works
     * without one, just with one fewer DH term -- see X3DH.initiate).
     * If publishing this bundle never actually reaches anyone (e.g. the
     * transport fails), this key is simply wasted; that's the same
     * tradeoff Signal's own server makes, and it's fine as long as the
     * pool gets replenished (not wired up yet).
     */
    public OneTimePreKey claimOneTimePreKeyForBundle() {
        try (Cursor cursor = db.query("OneTimePreKey", null, "used = 0", null, null, null, "keyId ASC", "1")) {
            if (!cursor.moveToFirst()) return null;
            int keyId = cursor.getInt(cursor.getColumnIndexOrThrow("keyId"));
            byte[] publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("publicKey"));
            byte[] privateKey = cursor.getBlob(cursor.getColumnIndexOrThrow("privateKey"));
            ContentValues values = new ContentValues();
            values.put("used", 1);
            db.update("OneTimePreKey", values, "keyId = ?", new String[]{String.valueOf(keyId)});
            return new OneTimePreKey(keyId, new X25519KeyPair(privateKey, publicKey));
        }
    }

    /**
     * For the responder side of a handshake: fetches the private key of a
     * one-time prekey named in an incoming X3DHInitialMessage. Returns null
     * if it's already been consumed or never existed (e.g. a replayed or
     * duplicate initial message) -- see X3DH.respond's note on what to do
     * with that null.
     */
    public OneTimePreKey getOneTimePreKeyIfUnused(int keyId) {
        try (Cursor cursor = db.query("OneTimePreKey", null, "keyId = ? AND used = 0",
                new String[]{String.valueOf(keyId)}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            byte[] publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("publicKey"));
            byte[] privateKey = cursor.getBlob(cursor.getColumnIndexOrThrow("privateKey"));
            return new OneTimePreKey(keyId, new X25519KeyPair(privateKey, publicKey));
        }
    }

    public void markOneTimePreKeyUsed(int keyId) {
        ContentValues values = new ContentValues();
        values.put("used", 1);
        db.update("OneTimePreKey", values, "keyId = ?", new String[]{String.valueOf(keyId)});
    }

    /**
     * Assembles the bundle we'd publish for others to fetch. Returns null
     * if ensurePreKeysExist() hasn't run yet. identityKeyX25519 isn't
     * stored anywhere -- it's cheap to recompute deterministically via
     * IdentityGenerator.deriveX25519IdentityKeys(identity), so the caller
     * passes it in rather than this method re-deriving it on every call.
     */
    public PreKeyBundle buildOurPreKeyBundle(Identity identity, byte[] identityKeyX25519) {
        SignedPreKey signedPreKey = getSignedPreKey();
        if (signedPreKey == null) return null;
        OneTimePreKey oneTimePreKey = claimOneTimePreKeyForBundle();
        return new PreKeyBundle(
                identity.peerId,
                identity.publicKey,
                identityKeyX25519,
                signedPreKey.keyId,
                signedPreKey.keyPair.publicKey,
                signedPreKey.signature,
                oneTimePreKey == null ? null : oneTimePreKey.keyId,
                oneTimePreKey == null ? null : oneTimePreKey.keyPair.publicKey
        );
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
