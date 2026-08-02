package com.mulechat.app.data;

public final class PeerContact {
    public final String peerId;
    public final String displayName;
    public final byte[] ed25519PublicKey;
    public final String lastKnownHost; // null if never seen on the LAN
    public final Integer lastKnownPort; // null if never seen on the LAN

    public PeerContact(String peerId, String displayName, byte[] ed25519PublicKey, String lastKnownHost, Integer lastKnownPort) {
        this.peerId = peerId;
        this.displayName = displayName;
        this.ed25519PublicKey = ed25519PublicKey;
        this.lastKnownHost = lastKnownHost;
        this.lastKnownPort = lastKnownPort;
    }
}
