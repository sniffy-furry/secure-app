package com.nulchat.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val SERVICE_TYPE = "_nulchat._tcp."
private const val TAG = "NulChatDiscovery"

actual class PeerDiscovery(private val context: Context) {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    actual fun startAdvertising(peerId: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "nulchat-$peerId"
            serviceType = SERVICE_TYPE
            setPort(port)
            // Peer ID travels in the service name (also settable as a TXT record
            // via setAttribute on API 21+, kept simple here).
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Advertising as ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Failed to advertise: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    actual fun stopAdvertising() {
        registrationListener?.let { nsdManager.unregisterService(it) }
        registrationListener = null
    }

    actual fun startDiscovery() {
        // Kept as a no-op trigger; actual discovery starts when observeDiscoveredPeers()
        // is collected (see callbackFlow below), matching Flow's cold-start semantics.
    }

    actual fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
    }

    actual fun observeDiscoveredPeers(): Flow<DiscoveredPeer> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceName.startsWith("nulchat-")) return
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolve failed for ${info.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val peerId = info.serviceName.removePrefix("nulchat-")
                        trySend(
                            DiscoveredPeer(
                                peerId = peerId,
                                host = info.host.hostAddress ?: return,
                                port = info.port
                            )
                        )
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) { /* Phase 3: mark peer offline */ }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("Discovery failed to start: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }
}
