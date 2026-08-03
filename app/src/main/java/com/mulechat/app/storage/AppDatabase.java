package com.mulechat.app.storage;

import android.content.Context;

import com.mulechat.app.data.NulChatRepository;

import net.sqlcipher.database.SQLiteDatabase;

/**
 * Process-wide handle to the encrypted database. The first call opens (or
 * creates) it -- which touches disk and the Keystore, so always call this
 * from a background thread, never the UI thread. Every call after the
 * first just returns the already-open connection.
 */
public final class AppDatabase {

    private static volatile NulChatRepository repository;

    private AppDatabase() {}

    public static synchronized NulChatRepository getOrOpen(Context context) throws Exception {
        if (repository == null) {
            char[] passphrase = PassphraseProvider.getOrCreatePassphrase(context.getApplicationContext());
            SQLiteDatabase db = NulChatDbHelper.open(context.getApplicationContext(), passphrase);
            repository = new NulChatRepository(db);
        }
        return repository;
    }
}
