package com.mulechat.app.storage;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

/**
 * Plain net.sqlcipher.database.SQLiteOpenHelper — same API shape as the
 * standard Android SQLiteOpenHelper, just backed by an encrypted file.
 * Call {@link #open(Context)} once (e.g. in Application.onCreate or the
 * first Activity) before using any repository methods.
 */
public final class NulChatDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "nulchat.db";
    private static final int DB_VERSION = 1;

    public NulChatDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /** Loads SQLCipher's native libs and opens (creating if needed) the encrypted DB. */
    public static SQLiteDatabase open(Context context, char[] passphrase) throws Exception {
        SQLiteDatabase.loadLibs(context);
        NulChatDbHelper helper = new NulChatDbHelper(context.getApplicationContext());
        return helper.getWritableDatabase(new String(passphrase));
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IdentityRow (" +
                        "id INTEGER PRIMARY KEY NOT NULL DEFAULT 0 CHECK (id = 0), " +
                        "publicKey BLOB NOT NULL, " +
                        "privateKey BLOB NOT NULL, " +
                        "peerId TEXT NOT NULL, " +
                        "displayName TEXT NOT NULL DEFAULT '', " +
                        "createdAtEpochMs INTEGER NOT NULL)"
        );

        db.execSQL(
                "CREATE TABLE Server (" +
                        "id TEXT PRIMARY KEY NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "iconUri TEXT, " +
                        "description TEXT NOT NULL DEFAULT '', " +
                        "ownerPeerId TEXT NOT NULL, " +
                        "createdAtEpochMs INTEGER NOT NULL)"
        );

        db.execSQL(
                "CREATE TABLE Channel (" +
                        "id TEXT PRIMARY KEY NOT NULL, " +
                        "serverId TEXT NOT NULL REFERENCES Server(id) ON DELETE CASCADE, " +
                        "name TEXT NOT NULL, " +
                        "position INTEGER NOT NULL DEFAULT 0)"
        );

        db.execSQL(
                "CREATE TABLE Peer (" +
                        "peerId TEXT PRIMARY KEY NOT NULL, " +
                        "displayName TEXT NOT NULL DEFAULT '', " +
                        "ed25519PublicKey BLOB NOT NULL, " +
                        "x25519IdentityKey BLOB, " +
                        "lastKnownHost TEXT, " +
                        "lastKnownPort INTEGER)"
        );

        db.execSQL(
                "CREATE TABLE Message (" +
                        "id TEXT PRIMARY KEY NOT NULL, " +
                        "peerId TEXT NOT NULL REFERENCES Peer(peerId) ON DELETE CASCADE, " +
                        "body TEXT NOT NULL, " +
                        "outgoing INTEGER NOT NULL, " +
                        "sentAtEpochMs INTEGER NOT NULL, " +
                        "deliveryState TEXT NOT NULL DEFAULT 'sent')"
        );

        db.execSQL(
                "CREATE TABLE RatchetSession (" +
                        "peerId TEXT PRIMARY KEY NOT NULL REFERENCES Peer(peerId) ON DELETE CASCADE, " +
                        "sessionBlob BLOB NOT NULL, " +
                        "updatedAtEpochMs INTEGER NOT NULL)"
        );

        // Our own X3DH prekeys -- see crypto.X3DH and data.NulChatRepository's
        // "X3DH prekeys" section. Single active row, same id=0 pattern as
        // IdentityRow; replaced wholesale on rotation (not implemented yet).
        db.execSQL(
                "CREATE TABLE SignedPreKey (" +
                        "id INTEGER PRIMARY KEY NOT NULL DEFAULT 0 CHECK (id = 0), " +
                        "keyId INTEGER NOT NULL, " +
                        "publicKey BLOB NOT NULL, " +
                        "privateKey BLOB NOT NULL, " +
                        "signature BLOB NOT NULL, " +
                        "createdAtEpochMs INTEGER NOT NULL)"
        );

        // Our pool of single-use prekeys. One gets claimed (and marked used)
        // each time we assemble a PreKeyBundle to publish.
        db.execSQL(
                "CREATE TABLE OneTimePreKey (" +
                        "keyId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "publicKey BLOB NOT NULL, " +
                        "privateKey BLOB NOT NULL, " +
                        "used INTEGER NOT NULL DEFAULT 0, " +
                        "createdAtEpochMs INTEGER NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No migrations yet — this is the first schema version.
    }
}
