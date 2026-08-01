package com.nulchat.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates (once) and retrieves the database passphrase, itself protected
 * by a hardware-backed key in the Android Keystore via Jetpack Security's
 * EncryptedSharedPreferences. The raw passphrase never touches disk in
 * plaintext and never needs to be typed by the user.
 */
object PassphraseProvider {
    private const val PREFS_NAME = "nulchat_secure_prefs"
    private const val KEY_ALIAS = "db_passphrase"

    fun getOrCreatePassphrase(context: Context): CharArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_ALIAS, null)
        if (existing != null) return existing.toCharArray()

        val fresh = generateRandomPassphrase()
        prefs.edit().putString(KEY_ALIAS, String(fresh)).apply()
        return fresh
    }

    private fun generateRandomPassphrase(length: Int = 32): CharArray {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return CharArray(length) { chars[random.nextInt(chars.length)] }
    }
}
