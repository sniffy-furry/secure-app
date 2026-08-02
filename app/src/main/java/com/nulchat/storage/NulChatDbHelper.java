package com.nulchat.storage;

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No migrations yet — this is the first schema version.
    }
}
