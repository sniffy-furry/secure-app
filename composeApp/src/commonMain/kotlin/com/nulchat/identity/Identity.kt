package com.nulchat.identity

/**
 * A user's self-sovereign cryptographic identity.
 *
 * - [publicKey] / [privateKey] are raw Ed25519 key bytes (32 bytes each).
 * - [peerId] is a human-shareable identifier derived from the public key
 *   (Blake2b hash, base32-encoded — similar in spirit to libp2p peer IDs).
 *
 * The private key never leaves the device unencrypted; it is stored only
 * inside the SQLCipher-encrypted local database (see storage/).
 */
data class Identity(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val peerId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Identity) return false
        return publicKey.contentEquals(other.publicKey) &&
            privateKey.contentEquals(other.privateKey) &&
            peerId == other.peerId
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        result = 31 * result + peerId.hashCode()
        return result
    }
}
