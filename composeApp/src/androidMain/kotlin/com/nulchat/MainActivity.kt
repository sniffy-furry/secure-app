package com.nulchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.nulchat.data.NulChatRepository
import com.nulchat.db.NulChatDatabase
import com.nulchat.net.DirectMessageService
import com.nulchat.storage.DatabaseDriverFactory
import com.nulchat.storage.PassphraseProvider
import com.nulchat.ui.App
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var directMessageService: DirectMessageService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val passphrase = PassphraseProvider.getOrCreatePassphrase(applicationContext)
        val driver = DatabaseDriverFactory(applicationContext).createDriver(passphrase)
        val database = NulChatDatabase(driver)
        val repository = NulChatRepository(database)

        // Once an identity exists (created during onboarding, or already on disk from a
        // previous launch), start advertising + discovering peers on the LAN.
        lifecycleScope.launch {
            val identity = repository.observeIdentity().filterNotNull().first()
            val service = DirectMessageService(
                context = applicationContext,
                repository = repository,
                myPeerId = identity.peerId,
                scope = lifecycleScope
            )
            directMessageService = service
            service.start()
        }

        setContent {
            App(
                repository = repository,
                onSendDirectMessage = { peerId, text ->
                    directMessageService?.sendMessage(peerId, text)
                        ?: error("Networking isn't ready yet — try again in a moment")
                }
            )
        }
    }

    override fun onDestroy() {
        directMessageService?.stop()
        super.onDestroy()
    }
}
