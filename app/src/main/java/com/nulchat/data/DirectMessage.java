package com.nulchat.data;

public final class DirectMessage {
    public final String id;
    public final String peerId;
    public final String body;
    public final boolean outgoing;
    public final long sentAtEpochMs;
    public final String deliveryState;

    public DirectMessage(String id, String peerId, String body, boolean outgoing, long sentAtEpochMs, String deliveryState) {
        this.id = id;
        this.peerId = peerId;
        this.body = body;
        this.outgoing = outgoing;
        this.sentAtEpochMs = sentAtEpochMs;
        this.deliveryState = deliveryState;
    }
}
