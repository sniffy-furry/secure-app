package com.nulchat.net

import com.nulchat.crypto.DoubleRatchetSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Deliberately simple wire protocol: one TCP connection per message, no
 * connection pooling, no retry queue for offline peers (that's the "relay
 * system" from the brief, section 5.6/9 — out of scope for this Phase 2
 * scaffold). Good enough for two devices on the same LAN, both online.
 *
 * Every frame starts with a 1-byte type:
 *   0 = HELLO    — first-contact handshake, carries an ephemeral X25519
 *                  public key IN THE CLEAR (no session exists yet to encrypt
 *                  it with). This is trust-on-first-use: it establishes a
 *                  shared secret via plain Diffie-Hellman but does NOT prove
 *                  the peer owns the identity key you think it does. Phase 3
 *                  should add a fingerprint/"safety number" verification step
 *                  before a HELLO is trusted for anything sensitive.
 *   1 = ENVELOPE  — an actual Double Ratchet-encrypted message.
 */
sealed class Frame {
    data class Hello(val fromPeerId: String, val ephemeralX25519PublicKey: ByteArray) : Frame()
    data class Message(val fromPeerId: String, val envelope: DoubleRatchetSession.Envelope) : Frame()
}

private const val FRAME_HELLO: Int = 0
private const val FRAME_MESSAGE: Int = 1

private fun DataOutputStream.writeLengthPrefixed(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readLengthPrefixed(): ByteArray {
    val length = readInt()
    require(length in 0..(16 * 1024 * 1024)) { "Refusing to read implausible frame length $length" }
    val bytes = ByteArray(length)
    readFully(bytes)
    return bytes
}

object MessageTransport {

    fun startServer(
        port: Int,
        scope: CoroutineScope,
        onFrameReceived: (Frame) -> Unit
    ): ServerSocket {
        val serverSocket = ServerSocket(port)
        scope.launch(Dispatchers.IO) {
            while (!serverSocket.isClosed) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    break // socket closed
                }
                launch(Dispatchers.IO) {
                    socket.use { runCatching { onFrameReceived(readFrame(it)) } }
                }
            }
        }
        return serverSocket
    }

    private fun readFrame(socket: Socket): Frame {
        val input = DataInputStream(socket.getInputStream())
        return when (val type = input.readInt()) {
            FRAME_HELLO -> Frame.Hello(
                fromPeerId = String(input.readLengthPrefixed(), Charsets.UTF_8),
                ephemeralX25519PublicKey = input.readLengthPrefixed()
            )
            FRAME_MESSAGE -> Frame.Message(
                fromPeerId = String(input.readLengthPrefixed(), Charsets.UTF_8),
                envelope = DoubleRatchetSession.Envelope(
                    senderRatchetPublicKey = input.readLengthPrefixed(),
                    previousChainLength = input.readInt(),
                    messageNumber = input.readInt(),
                    nonce = input.readLengthPrefixed(),
                    ciphertext = input.readLengthPrefixed()
                )
            )
            else -> error("Unknown frame type $type")
        }
    }

    suspend fun sendHello(host: String, port: Int, fromPeerId: String, ephemeralX25519PublicKey: ByteArray) =
        withContext(Dispatchers.IO) {
            Socket(host, port).use { socket ->
                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(FRAME_HELLO)
                output.writeLengthPrefixed(fromPeerId.toByteArray(Charsets.UTF_8))
                output.writeLengthPrefixed(ephemeralX25519PublicKey)
                output.flush()
            }
        }

    suspend fun sendEnvelope(host: String, port: Int, fromPeerId: String, envelope: DoubleRatchetSession.Envelope) =
        withContext(Dispatchers.IO) {
            Socket(host, port).use { socket ->
                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(FRAME_MESSAGE)
                output.writeLengthPrefixed(fromPeerId.toByteArray(Charsets.UTF_8))
                output.writeLengthPrefixed(envelope.senderRatchetPublicKey)
                output.writeInt(envelope.previousChainLength)
                output.writeInt(envelope.messageNumber)
                output.writeLengthPrefixed(envelope.nonce)
                output.writeLengthPrefixed(envelope.ciphertext)
                output.flush()
            }
        }
}
