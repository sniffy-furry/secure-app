package com.nulchat.net

import android.content.Context
import com.nulchat.crypto.CryptoPrimitives
import com.nulchat.crypto.DoubleRatchetSession
import com.nulchat.data.NulChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.net.ServerSocket

private const val LISTEN_PORT = 47110

/**
 * Ties [PeerDiscovery] (LAN mDNS), [MessageTransport] (TCP), and
 * [DoubleRatchetSession] (encryption) together into "send/receive DMs".
 *
 * PAIRING MODEL (read before relying on this for anything sensitive):
 * There's no X3DH and no identity verification yet. The first time two
 * instances of the app see each other on the LAN, they exchange a HELLO
 * frame carrying an ephemeral X25519 public key IN THE CLEAR, and derive a
 * shared secret via plain Diffie-Hellman from those ephemeral keys. This
 * protects against a passive eavesdropper but NOT against an active
 * man-in-the-middle on that very first LAN handshake — there's no check yet
 * that the peer answering to a given peerId actually controls that peerId's
 * long-term Ed25519 identity key. A real implementation should have both
 * sides display a short fingerprint (derived from both public keys) that
 * the users compare out of band before trusting the conversation — the same
 * idea as Signal's "safety numbers". That verification step, plus a proper
 * X3DH handshake, is the natural next piece of work here.
 *
 * One handshake keypair is generated per app run and reused for every peer
 * (rather than one-time prekeys per peer) — simpler, at the cost of a bit
 * less forward secrecy for that very first message than the full spec gives.
 */
class DirectMessageService(
    private val context: Context,
    private val repository: NulChatRepository,
    private val myPeerId: String,
    private val scope: CoroutineScope
) {
    private val discovery = PeerDiscovery(context)
    private var serverSocket: ServerSocket? = null

    private val sessions = mutableMapOf<String, DoubleRatchetSession>()
    private val pendingHandshakeKeys = mutableMapOf<String, ByteArray>()
    private val helloSentTo = mutableSetOf<String>()

    private val ourHandshakeSecret = CryptoPrimitives.generateX25519SecretKey()
    private val ourHandshakePublic = CryptoPrimitives.x25519PublicFromSecret(ourHandshakeSecret)

    fun start() {
        serverSocket = MessageTransport.startServer(LISTEN_PORT, scope) { frame -> handleFrame(frame) }
        discovery.startAdvertising(myPeerId, LISTEN_PORT)
        scope.launch {
            discovery.observeDiscoveredPeers().collect { discovered ->
                if (discovered.peerId == myPeerId) return@collect
                repository.updatePeerNetworkLocation(discovered.peerId, discovered.host, discovered.port.toLong())
                if (helloSentTo.add(discovered.peerId)) {
                    runCatching {
                        MessageTransport.sendHello(discovered.host, discovered.port, myPeerId, ourHandshakePublic)
                    }
                }
            }
        }
    }

    fun stop() {
        discovery.stopAdvertising()
        discovery.stopDiscovery()
        runCatching { serverSocket?.close() }
    }

    private fun handleFrame(frame: Frame) {
        when (frame) {
            is Frame.Hello -> {
                pendingHandshakeKeys[frame.fromPeerId] = frame.ephemeralX25519PublicKey
                if (repository.getPeer(frame.fromPeerId) == null) {
                    // Placeholder identity until Phase 3 adds a real identity-verification
                    // step for peers first met via LAN discovery rather than an invite link.
                    repository.upsertPeerIdentity(
                        peerId = frame.fromPeerId,
                        displayName = frame.fromPeerId.take(8),
                        ed25519PublicKey = ByteArray(0)
                    )
                }
            }

            is Frame.Message -> {
                val theirHandshakeKey = pendingHandshakeKeys[frame.fromPeerId]
                if (theirHandshakeKey == null) {
                    // Received a message from a peer we never handshook with — drop it.
                    return
                }
                val session = sessions.getOrPut(frame.fromPeerId) {
                    val sharedSecret = CryptoPrimitives.diffieHellman(ourHandshakeSecret, theirHandshakeKey)
                    DoubleRatchetSession.initAsResponder(
                        sharedSecret = sharedSecret,
                        ourRatchetSecretKey = ourHandshakeSecret,
                        ourRatchetPublicKey = ourHandshakePublic
                    )
                }
                val plaintext = runCatching { session.decrypt(frame.envelope) }.getOrNull() ?: return
                repository.insertMessage(
                    peerId = frame.fromPeerId,
                    body = plaintext.toString(Charsets.UTF_8),
                    outgoing = false,
                    deliveryState = "received"
                )
            }
        }
    }

    /** Throws if we haven't completed a handshake with this peer yet (see class doc). */
    suspend fun sendMessage(peerId: String, text: String) {
        val session = sessions.getOrPut(peerId) {
            val theirHandshakeKey = pendingHandshakeKeys[peerId]
                ?: error("No handshake with $peerId yet — wait for LAN discovery to find them first")
            val sharedSecret = CryptoPrimitives.diffieHellman(ourHandshakeSecret, theirHandshakeKey)
            DoubleRatchetSession.initAsInitiator(sharedSecret, theirHandshakeKey)
        }
        val peer = repository.getPeer(peerId) ?: error("Unknown peer $peerId")
        val host = peer.lastKnownHost ?: error("No known network location for $peerId (are they online?)")
        val port = peer.lastKnownPort ?: error("No known network location for $peerId (are they online?)")

        val envelope = session.encrypt(text.toByteArray(Charsets.UTF_8))
        repository.insertMessage(peerId = peerId, body = text, outgoing = true, deliveryState = "sent")
        MessageTransport.sendEnvelope(host, port.toInt(), myPeerId, envelope)
    }
}
