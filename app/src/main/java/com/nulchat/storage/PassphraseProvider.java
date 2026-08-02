package com.nulchat.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Generates (once) and retrieves the database passphrase, itself protected
 * by a hardware-backed key in the Android Keystore via Jetpack Security's
 * EncryptedSharedPreferences. The raw passphrase never touches disk in
 * plaintext and never needs to be typed by the user.
 */
public final class PassphraseProvider {

    private static final String PREFS_NAME = "nulchat_secure_prefs";
    private static final String KEY_ALIAS = "db_passphrase";

    private PassphraseProvider() {}

    public static char[] getOrCreatePassphrase(Context context) throws GeneralSecurityException, java.io.IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        SharedPreferences prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );

        String existing = prefs.getString(KEY_ALIAS, null);
        if (existing != null) {
            return existing.toCharArray();
        }

        char[] fresh = generateRandomPassphrase(32);
        prefs.edit().putString(KEY_ALIAS, new String(fresh)).apply();
        return fresh;
    }

    private static char[] generateRandomPassphrase(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = chars.charAt(random.nextInt(chars.length()));
        }
        return out;
    }
}
