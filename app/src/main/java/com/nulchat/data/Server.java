package com.nulchat.data;

public final class Server {
    public final String id;
    public final String name;
    public final String iconUri;
    public final String description;
    public final String ownerPeerId;
    public final long createdAtEpochMs;

    public Server(String id, String name, String iconUri, String description, String ownerPeerId, long createdAtEpochMs) {
        this.id = id;
        this.name = name;
        this.iconUri = iconUri;
        this.description = description;
        this.ownerPeerId = ownerPeerId;
        this.createdAtEpochMs = createdAtEpochMs;
    }
}
