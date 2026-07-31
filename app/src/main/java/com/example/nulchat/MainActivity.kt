package com.example.mulechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.security.KeyPairGenerator
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Generate your Ed25519 identity (the exact same math as the Python script!)
        val keyPair = KeyPairGenerator.getInstance("Ed25519").genKeyPair()
        val publicKey = keyPair.public.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
        val peerId = digest.joinToString("") { "%02x".format(it) }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("🔐 Project Chat", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your Native Peer ID:", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = peerId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text("✅ This is a real native Android app.", style = MaterialTheme.typography.bodyMedium)
                        Text("Built via GitHub Actions.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}