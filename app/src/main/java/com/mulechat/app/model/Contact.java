package com.mulechat.app.model;

/**
 * A contact as shown in the UI shell. peerIdHint stands in for the real
 * blake3(pubkey) peer id described in the plan's identity section --
 * here it's just whatever string the user pasted into the invite field.
 */
public class Contact {
    public final String peerIdHint;
    public final String nickname;
    public final int accentColor;

    public Contact(String peerIdHint, String nickname, int accentColor) {
        this.peerIdHint = peerIdHint;
        this.nickname = nickname;
        this.accentColor = accentColor;
    }
}
