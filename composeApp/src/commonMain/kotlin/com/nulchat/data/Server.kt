package com.nulchat.data

data class Server(
    val id: String,
    val name: String,
    val iconUri: String?,
    val description: String,
    val ownerPeerId: String,
    val createdAtEpochMs: Long
)

data class Channel(
    val id: String,
    val serverId: String,
    val name: String,
    val position: Long
)
