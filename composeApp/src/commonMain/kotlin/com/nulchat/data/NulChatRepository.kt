package com.nulchat.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.nulchat.db.NulChatDatabase
import com.nulchat.identity.Identity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

class NulChatRepository(
    private val database: NulChatDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val queries = database.nulChatQueries

    fun observeIdentity(): Flow<Identity?> =
        queries.selectIdentity()
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .let { flow ->
                kotlinx.coroutines.flow.channelFlow {
                    flow.collect { row ->
                        send(
                            row?.let {
                                Identity(
                                    publicKey = it.publicKey,
                                    privateKey = it.privateKey,
                                    peerId = it.peerId
                                )
                            }
                        )
                    }
                }
            }

    fun saveIdentity(identity: Identity, displayName: String = "") {
        queries.insertIdentity(
            publicKey = identity.publicKey,
            privateKey = identity.privateKey,
            peerId = identity.peerId,
            displayName = displayName,
            createdAtEpochMs = currentTimeMillis()
        )
    }

    fun observeServers(): Flow<List<Server>> =
        queries.selectAllServers()
            .asFlow()
            .mapToList(ioDispatcher)
            .let { flow ->
                kotlinx.coroutines.flow.channelFlow {
                    flow.collect { rows ->
                        send(
                            rows.map {
                                Server(
                                    id = it.id,
                                    name = it.name,
                                    iconUri = it.iconUri,
                                    description = it.description,
                                    ownerPeerId = it.ownerPeerId,
                                    createdAtEpochMs = it.createdAtEpochMs
                                )
                            }
                        )
                    }
                }
            }

    fun createServer(name: String, ownerPeerId: String, description: String = "", iconUri: String? = null): String {
        val id = randomId()
        queries.insertServer(
            id = id,
            name = name,
            iconUri = iconUri,
            description = description,
            ownerPeerId = ownerPeerId,
            createdAtEpochMs = currentTimeMillis()
        )
        return id
    }

    fun observePeers(): Flow<List<PeerContact>> =
        queries.selectAllPeers()
            .asFlow()
            .mapToList(ioDispatcher)
            .let { flow ->
                kotlinx.coroutines.flow.channelFlow {
                    flow.collect { rows ->
                        send(
                            rows.map {
                                PeerContact(
                                    peerId = it.peerId,
                                    displayName = it.displayName,
                                    ed25519PublicKey = it.ed25519PublicKey,
                                    lastKnownHost = it.lastKnownHost,
                                    lastKnownPort = it.lastKnownPort
                                )
                            }
                        )
                    }
                }
            }

    fun getPeer(peerId: String): PeerContact? =
        queries.selectPeer(peerId).executeAsOneOrNull()?.let {
            PeerContact(
                peerId = it.peerId,
                displayName = it.displayName,
                ed25519PublicKey = it.ed25519PublicKey,
                lastKnownHost = it.lastKnownHost,
                lastKnownPort = it.lastKnownPort
            )
        }

    /** Adds a peer to our contact list, or updates their display name if already known. */
    fun upsertPeerIdentity(peerId: String, displayName: String, ed25519PublicKey: ByteArray) {
        val existing = queries.selectPeer(peerId).executeAsOneOrNull()
        queries.insertOrUpdatePeer(
            peerId = peerId,
            displayName = displayName,
            ed25519PublicKey = ed25519PublicKey,
            lastKnownHost = existing?.lastKnownHost,
            lastKnownPort = existing?.lastKnownPort,
            ratchetRootKey = existing?.ratchetRootKey,
            ratchetSelfSecretKey = existing?.ratchetSelfSecretKey,
            ratchetSelfPublicKey = existing?.ratchetSelfPublicKey,
            ratchetRemotePublicKey = existing?.ratchetRemotePublicKey,
            ratchetSendChainKey = existing?.ratchetSendChainKey,
            ratchetRecvChainKey = existing?.ratchetRecvChainKey,
            ratchetSendMessageNumber = existing?.ratchetSendMessageNumber ?: 0,
            ratchetRecvMessageNumber = existing?.ratchetRecvMessageNumber ?: 0
        )
    }

    fun updatePeerNetworkLocation(peerId: String, host: String, port: Long) {
        queries.updatePeerNetworkLocation(host, port, peerId)
    }

    fun observeMessages(peerId: String): Flow<List<DirectMessage>> =
        queries.selectMessagesForPeer(peerId)
            .asFlow()
            .mapToList(ioDispatcher)
            .let { flow ->
                kotlinx.coroutines.flow.channelFlow {
                    flow.collect { rows ->
                        send(
                            rows.map {
                                DirectMessage(
                                    id = it.id,
                                    peerId = it.peerId,
                                    body = it.body,
                                    outgoing = it.outgoing == 1L,
                                    sentAtEpochMs = it.sentAtEpochMs,
                                    deliveryState = it.deliveryState
                                )
                            }
                        )
                    }
                }
            }

    fun insertMessage(peerId: String, body: String, outgoing: Boolean, deliveryState: String = "sent"): String {
        val id = randomId()
        queries.insertMessage(
            id = id,
            peerId = peerId,
            body = body,
            outgoing = if (outgoing) 1L else 0L,
            sentAtEpochMs = currentTimeMillis(),
            deliveryState = deliveryState
        )
        return id
    }

    private fun randomId(): String {
        val bytes = ByteArray(16)
        Random.Default.nextBytes(bytes)
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(32)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(hexChars[v shr 4])
            sb.append(hexChars[v and 0x0F])
        }
        return sb.toString()
    }
}

expect fun currentTimeMillis(): Long
