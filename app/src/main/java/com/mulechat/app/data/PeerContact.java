package com.mulechat.app.data;

public final class PeerContact {
    public final String peerId;
    public final String displayName;
    public final byte[] ed25519PublicKey;
    public final byte[] x25519IdentityKey; // null until we've pinned this peer's first PreKeyBundle (see X3DH)
    public final String lastKnownHost; // null if never seen on the LAN
    public final Integer lastKnownPort; // null if never seen on the LAN

    public PeerContact(String peerId, String displayName, byte[] ed25519PublicKey, byte[] x25519IdentityKey,
                        String lastKnownHost, Integer lastKnownPort) {
        this.peerId = peerId;
        this.displayName = displayName;
        this.ed25519PublicKey = ed25519PublicKey;
        this.x25519IdentityKey = x25519IdentityKey;
        this.lastKnownHost = lastKnownHost;
        this.lastKnownPort = lastKnownPort;
    }
}
