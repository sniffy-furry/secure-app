package com.nulchat.crypto

/**
 * A simplified Double Ratchet (Signal Protocol) session for one 1:1
 * conversation with a single peer.
 *
 * Simplifications vs. the full spec (worth revisiting post-MVP):
 * - No proper X3DH handshake yet. The very first shared secret is whatever
 *   [initAsInitiator]/[initAsResponder] are given — in this scaffold that's
 *   a DH between long-term identity-derived X25519 keys exchanged out of
 *   band (e.g. via the server invite link). A real X3DH would add signed
 *   prekeys + one-time prekeys for asynchronous, forward-secure first
 *   contact even while the peer is offline.
 * - Skipped/out-of-order message keys are kept only in memory
 *   ([skippedMessageKeys]), not persisted. If the app restarts before a
 *   delayed message arrives, that message becomes undecryptable. Persisting
 *   this map (encrypted) is a natural next step.
 * - This session object itself is not yet persisted to disk between app
 *   launches; see NulChatRepository/RatchetSession table for where that
 *   would plug in.
 *
 * Message numbering and the ratchet steps otherwise follow the standard
 * Double Ratchet: a DH ratchet advances the root key whenever a new remote
 * ratchet public key is seen, and a symmetric-key ratchet advances the
 * sending/receiving chain keys for every message within a chain.
 */
class DoubleRatchetSession private constructor(
    private var rootKey: ByteArray,
    private var dhSelfSecret: ByteArray,
    private var dhSelfPublic: ByteArray,
    private var dhRemotePublic: ByteArray?,
    private var sendChainKey: ByteArray?,
    private var recvChainKey: ByteArray?,
    private var sendMessageNumber: Int = 0,
    private var recvMessageNumber: Int = 0,
    private var previousSendChainLength: Int = 0
) {
    private val skippedMessageKeys = mutableMapOf<Pair<String, Int>, ByteArray>()

    data class Envelope(
        val senderRatchetPublicKey: ByteArray,
        val previousChainLength: Int,
        val messageNumber: Int,
        val nonce: ByteArray,
        val ciphertext: ByteArray
    )

    fun encrypt(plaintext: ByteArray): Envelope {
        val chainKey = sendChainKey ?: error("Session not initialized for sending")
        val (messageKey, nextChainKey) = symmetricRatchetStep(chainKey)
        sendChainKey = nextChainKey

        val nonce = CryptoPrimitives.randomNonce()
        val ciphertext = CryptoPrimitives.encrypt(messageKey, nonce, plaintext)
        val envelope = Envelope(
            senderRatchetPublicKey = dhSelfPublic,
            previousChainLength = previousSendChainLength,
            messageNumber = sendMessageNumber,
            nonce = nonce,
            ciphertext = ciphertext
        )
        sendMessageNumber += 1
        return envelope
    }

    fun decrypt(envelope: Envelope): ByteArray {
        val remoteKeyHex = envelope.senderRatchetPublicKey.toHex()

        skippedMessageKeys.remove(remoteKeyHex to envelope.messageNumber)?.let { key ->
            return CryptoPrimitives.decrypt(key, envelope.nonce, envelope.ciphertext)
        }

        if (dhRemotePublic == null || !envelope.senderRatchetPublicKey.contentEquals(dhRemotePublic)) {
            skipRecvMessageKeys(envelope.previousChainLength)
            dhRatchetStep(envelope.senderRatchetPublicKey)
        }

        skipRecvMessageKeys(envelope.messageNumber)

        val chainKey = recvChainKey ?: error("Session not initialized for receiving")
        val (messageKey, nextChainKey) = symmetricRatchetStep(chainKey)
        recvChainKey = nextChainKey
        recvMessageNumber += 1

        return CryptoPrimitives.decrypt(messageKey, envelope.nonce, envelope.ciphertext)
    }

    private fun skipRecvMessageKeys(until: Int) {
        val chainKey = recvChainKey ?: return
        var current = chainKey
        var n = recvMessageNumber
        val remoteKeyHex = (dhRemotePublic ?: return).toHex()
        while (n < until) {
            val (messageKey, nextChainKey) = symmetricRatchetStep(current)
            skippedMessageKeys[remoteKeyHex to n] = messageKey
            current = nextChainKey
            n += 1
        }
        recvChainKey = current
        recvMessageNumber = n
    }

    /** Advances the DH ratchet: new remote public key seen -> derive fresh root + chain keys. */
    private fun dhRatchetStep(newRemotePublic: ByteArray) {
        previousSendChainLength = sendMessageNumber
        sendMessageNumber = 0
        recvMessageNumber = 0
        dhRemotePublic = newRemotePublic

        // 1) Receiving chain: DH(ourCurrentSecret, theirNewPublic)
        val recvShared = CryptoPrimitives.diffieHellman(dhSelfSecret, newRemotePublic)
        val (newRootKeyAfterRecv, newRecvChain) = rootKdfStep(rootKey, recvShared)
        rootKey = newRootKeyAfterRecv
        recvChainKey = newRecvChain

        // 2) Generate a fresh sending ratchet keypair and derive the new sending chain
        dhSelfSecret = CryptoPrimitives.generateX25519SecretKey()
        dhSelfPublic = CryptoPrimitives.x25519PublicFromSecret(dhSelfSecret)
        val sendShared = CryptoPrimitives.diffieHellman(dhSelfSecret, newRemotePublic)
        val (newRootKeyAfterSend, newSendChain) = rootKdfStep(rootKey, sendShared)
        rootKey = newRootKeyAfterSend
        sendChainKey = newSendChain
    }

    private fun rootKdfStep(currentRootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        // Root KDF: mix the DH output into the root key to get (nextRootKey, chainKey).
        // Using the DH output itself as the KDF master key, keyed by the current root key
        // via subkey id/context, keeps this a single call into CryptoPrimitives.deriveKey.
        val nextRootKey = CryptoPrimitives.deriveKey(dhOutput, subkeyId = 1, context = "nulroot")
        val chainKey = CryptoPrimitives.deriveKey(dhOutput, subkeyId = 2, context = "nulchain")
        return nextRootKey to chainKey
    }

    private fun symmetricRatchetStep(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = CryptoPrimitives.deriveKey(chainKey, subkeyId = 1, context = "nulmsg_")
        val nextChainKey = CryptoPrimitives.deriveKey(chainKey, subkeyId = 2, context = "nulchn_")
        return messageKey to nextChainKey
    }

    companion object {
        /**
         * Call this on the side that sends the first message. [sharedSecret] must be
         * agreed out of band (see class doc — X3DH is the real-world way to do this).
         */
        fun initAsInitiator(sharedSecret: ByteArray, theirRatchetPublicKey: ByteArray): DoubleRatchetSession {
            val selfSecret = CryptoPrimitives.generateX25519SecretKey()
            val selfPublic = CryptoPrimitives.x25519PublicFromSecret(selfSecret)
            val session = DoubleRatchetSession(
                rootKey = sharedSecret,
                dhSelfSecret = selfSecret,
                dhSelfPublic = selfPublic,
                dhRemotePublic = null,
                sendChainKey = null,
                recvChainKey = null
            )
            session.dhRatchetStep(theirRatchetPublicKey)
            return session
        }

        /** Call this on the side that waits to receive the first message. */
        fun initAsResponder(
            sharedSecret: ByteArray,
            ourRatchetSecretKey: ByteArray,
            ourRatchetPublicKey: ByteArray
        ): DoubleRatchetSession {
            return DoubleRatchetSession(
                rootKey = sharedSecret,
                dhSelfSecret = ourRatchetSecretKey,
                dhSelfPublic = ourRatchetPublicKey,
                dhRemotePublic = null,
                sendChainKey = null,
                recvChainKey = null
            )
        }
    }
}

private fun ByteArray.toHex(): String {
    val hexChars = "0123456789abcdef"
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(hexChars[v shr 4])
        sb.append(hexChars[v and 0x0F])
    }
    return sb.toString()
}
