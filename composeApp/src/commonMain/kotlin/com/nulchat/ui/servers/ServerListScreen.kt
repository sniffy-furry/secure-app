package com.nulchat.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nulchat.data.Server
import com.nulchat.ui.theme.AccentPurple
import com.nulchat.ui.theme.BackgroundPanel

/**
 * Left "server rail" + a placeholder main pane, matching the layout sketched
 * in the brief (section 4.2).
 */
@Composable
fun ServerListScreen(
    servers: List<Server>,
    onCreateServer: (name: String) -> Unit,
    onSelectServer: (Server) -> Unit,
    onOpenDirectMessages: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .background(BackgroundPanel)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DirectMessagesButton(onClick = onOpenDirectMessages)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.width(32.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(servers) { server ->
                    ServerIcon(server = server, onClick = { onSelectServer(server) })
                }
            }
            Spacer(Modifier.height(8.dp))
            AddServerButton(onClick = { showCreateDialog = true })
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (servers.isEmpty()) "No servers yet — tap + to create one" else "Select a server",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (showCreateDialog) {
        CreateServerDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateServer(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ServerIcon(server: Server, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(AccentPurple)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(server.name.take(2).uppercase(), color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
private fun DirectMessagesButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("DM", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AddServerButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Create server")
    }
}

@Composable
private fun CreateServerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a server") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Server name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
