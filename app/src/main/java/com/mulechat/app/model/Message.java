package com.mulechat.app.model;

/**
 * A message bubble shown in the UI shell. This is in-memory only and
 * disappears when ConversationActivity is destroyed -- there is no
 * Double Ratchet, no wire protocol, and no persistence wired up yet.
 */
public class Message {
    public final String text;
    public final boolean fromMe;
    public final long timestampMillis;

    public Message(String text, boolean fromMe, long timestampMillis) {
        this.text = text;
        this.fromMe = fromMe;
        this.timestampMillis = timestampMillis;
    }
}
