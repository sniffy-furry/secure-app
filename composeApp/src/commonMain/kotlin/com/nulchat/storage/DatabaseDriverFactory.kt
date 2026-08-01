package com.nulchat.storage

import app.cash.sqldelight.db.SqlDriver

/**
 * Each platform provides an SqlDriver backed by an ENCRYPTED database
 * (SQLCipher on Android). [passphrase] should be derived from the user's
 * identity/device keystore, never hardcoded and never stored in plaintext.
 */
expect class DatabaseDriverFactory {
    fun createDriver(passphrase: CharArray): SqlDriver
}
