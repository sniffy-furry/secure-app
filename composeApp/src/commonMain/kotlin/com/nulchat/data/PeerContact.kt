package com.nulchat.data

data class PeerContact(
    val peerId: String,
    val displayName: String,
    val ed25519PublicKey: ByteArray,
    val lastKnownHost: String?,
    val lastKnownPort: Long?
)

data class DirectMessage(
    val id: String,
    val peerId: String,
    val body: String,
    val outgoing: Boolean,
    val sentAtEpochMs: Long,
    val deliveryState: String
)
