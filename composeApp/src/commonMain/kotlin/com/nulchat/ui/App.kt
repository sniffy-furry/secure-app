package com.nulchat.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nulchat.data.NulChatRepository
import com.nulchat.data.PeerContact
import com.nulchat.identity.Identity
import com.nulchat.identity.IdentityGenerator
import com.nulchat.ui.chat.ChatScreen
import com.nulchat.ui.onboarding.OnboardingScreen
import com.nulchat.ui.peers.PeerListScreen
import com.nulchat.ui.servers.ServerListScreen
import com.nulchat.ui.theme.NulChatTheme
import kotlinx.coroutines.launch

private sealed class Screen {
    data object Servers : Screen()
    data object Peers : Screen()
    data class Chat(val peer: PeerContact) : Screen()
}

/**
 * [onSendDirectMessage] is injected from the Android side (MainActivity),
 * where the actual networking (DirectMessageService) lives — see Phase 2
 * README notes on why networking is Android-only for now.
 */
@Composable
fun App(
    repository: NulChatRepository,
    onSendDirectMessage: suspend (peerId: String, text: String) -> Unit
) {
    NulChatTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val identity by repository.observeIdentity().collectAsState(initial = null)
            val servers by repository.observeServers().collectAsState(initial = emptyList())
            val peers by repository.observePeers().collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()
            var screen by remember { mutableStateOf<Screen>(Screen.Servers) }

            if (identity == null) {
                OnboardingScreen(
                    onIdentityCreated = { _, words ->
                        scope.launch {
                            val newIdentity: Identity = IdentityGenerator.fromSeedPhrase(words)
                            repository.saveIdentity(newIdentity)
                        }
                    }
                )
                return@Surface
            }

            when (val current = screen) {
                is Screen.Servers -> ServerListScreen(
                    servers = servers,
                    onCreateServer = { name ->
                        repository.createServer(name = name, ownerPeerId = identity!!.peerId)
                    },
                    onSelectServer = { /* Phase 3: channel/roles UI */ },
                    onOpenDirectMessages = { screen = Screen.Peers }
                )

                is Screen.Peers -> PeerListScreen(
                    peers = peers,
                    onSelectPeer = { peer -> screen = Screen.Chat(peer) }
                )

                is Screen.Chat -> {
                    val messages by repository.observeMessages(current.peer.peerId)
                        .collectAsState(initial = emptyList())
                    ChatScreen(
                        peerDisplayName = current.peer.displayName,
                        messages = messages,
                        onSend = { text ->
                            scope.launch { onSendDirectMessage(current.peer.peerId, text) }
                        },
                        onBack = { screen = Screen.Peers }
                    )
                }
            }
        }
    }
}
