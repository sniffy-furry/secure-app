package com.nulchat.net

import kotlinx.coroutines.flow.Flow

/** A peer found on the local network, before we know if we already trust it. */
data class DiscoveredPeer(
    val peerId: String,
    val host: String,
    val port: Int
)

/**
 * LAN discovery via mDNS/DNS-SD (Android: NsdManager). This is the "opportunistic
 * LAN discovery" piece of the brief's networking stack (section 5.3); internet-wide
 * discovery via a Kademlia DHT is intentionally out of scope for Phase 2.
 */
expect class PeerDiscovery {
    /** Advertises our own peerId + listening port so others can find us. */
    fun startAdvertising(peerId: String, port: Int)
    fun stopAdvertising()

    /** Emits peers as they're found/updated on the LAN. */
    fun observeDiscoveredPeers(): Flow<DiscoveredPeer>
    fun startDiscovery()
    fun stopDiscovery()
}
