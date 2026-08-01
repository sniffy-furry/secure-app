package com.nulchat.ui.peers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nulchat.data.PeerContact
import com.nulchat.ui.theme.OnlineGreen

@Composable
fun PeerListScreen(
    peers: List<PeerContact>,
    onSelectPeer: (PeerContact) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Direct Messages") })

        if (peers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No one found yet. NulChat looks for other NulChat users on the " +
                        "same Wi-Fi/LAN automatically \u2014 keep this screen open for a moment.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn {
                items(peers) { peer ->
                    PeerRow(peer = peer, onClick = { onSelectPeer(peer) })
                }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: PeerContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(peer.displayName.take(2).uppercase())
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(peer.peerId, style = MaterialTheme.typography.labelSmall)
        }
        if (peer.lastKnownHost != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
        }
    }
}
