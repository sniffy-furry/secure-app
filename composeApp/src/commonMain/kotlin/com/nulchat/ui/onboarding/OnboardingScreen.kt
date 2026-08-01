package com.nulchat.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nulchat.identity.IdentityGenerator
import com.nulchat.identity.SeedPhrase
import kotlinx.coroutines.launch

/**
 * First-launch screen. No email, no phone number — just generates a
 * keypair on-device and shows the recovery phrase exactly once.
 */
@Composable
fun OnboardingScreen(
    onIdentityCreated: (privateKeySeed: ByteArray, words: List<String>) -> Unit
) {
    var isGenerating by remember { mutableStateOf(false) }
    var revealedWords by remember { mutableStateOf<List<String>?>(null) }
    var pendingSeed by remember { mutableStateOf<ByteArray?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to NulChat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Private, peer-to-peer chat. No phone number, no email — just you and your keys.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(32.dp))

        if (revealedWords == null) {
            Button(
                enabled = !isGenerating,
                onClick = {
                    isGenerating = true
                    scope.launch {
                        val (seed, words) = SeedPhrase.generate()
                        pendingSeed = seed
                        revealedWords = words
                        isGenerating = false
                    }
                }
            ) {
                Text(if (isGenerating) "Generating..." else "Create my identity")
            }
        } else {
            Text(
                "Write down these 24 words in order and keep them somewhere safe. " +
                    "This is the ONLY way to recover your account — NulChat has no " +
                    "central server and cannot reset it for you.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            SeedPhraseGrid(revealedWords!!)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                onIdentityCreated(pendingSeed!!, revealedWords!!)
            }) {
                Text("I've saved my phrase — continue")
            }
        }
    }
}

@Composable
private fun SeedPhraseGrid(words: List<String>) {
    Column {
        words.chunked(3).forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEachIndexed { colIndex, word ->
                    val number = rowIndex * 3 + colIndex + 1
                    Text("$number. $word", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
