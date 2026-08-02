package com.nulchat.data;

public final class Channel {
    public final String id;
    public final String serverId;
    public final String name;
    public final long position;

    public Channel(String id, String serverId, String name, long position) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.position = position;
    }
}
