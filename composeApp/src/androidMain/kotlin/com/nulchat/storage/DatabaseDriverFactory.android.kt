package com.nulchat.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.nulchat.db.NulChatDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(passphrase: CharArray): SqlDriver {
        // Load SQLCipher's native libs once per process.
        SQLiteDatabase.loadLibs(context)

        val supportFactory = SupportFactory(
            SQLiteDatabase.getBytes(passphrase)
        )

        return AndroidSqliteDriver(
            schema = NulChatDatabase.Schema,
            context = context,
            name = "nulchat.db",
            factory = supportFactory
        )
    }
}
