package com.mulechat.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

/**
 * Local-only storage for shell/demo identity, profile, and settings fields.
 *
 * IMPORTANT: the "peer id" generated here is a random placeholder, NOT
 * derived from a real Ed25519 keypair, and "import" does not actually
 * restore anything -- it just fabricates a new placeholder id so the UI
 * has something to display. See the plan's identity section for the real
 * design (Ed25519 keypair, blake3(pubkey) peer id, encrypted seed-phrase
 * export/import). Nothing in this class is cryptography.
 */
public class IdentityStore {
    private static final String PREFS = "mulechat_shell_prefs";
    private static final String KEY_PEER_ID = "peer_id_placeholder";
    private static final String KEY_HAS_IDENTITY = "has_identity";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_BIO = "bio";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ACCENT = "accent_color";
    private static final String KEY_RELAY_ENABLED = "relay_enabled";
    private static final String KEY_RELAY_WIFI_ONLY = "relay_wifi_only";
    private static final String KEY_RELAY_CHARGING_ONLY = "relay_charging_only";
    private static final String KEY_RELAY_MAX_MB = "relay_max_mb";
    private static final String KEY_RELAY_MIN_BATTERY = "relay_min_battery";

    private final SharedPreferences prefs;

    public IdentityStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasIdentity() {
        return prefs.getBoolean(KEY_HAS_IDENTITY, false);
    }

    /** Generates a placeholder peer id and marks identity as "created." */
    public String createPlaceholderIdentity() {
        String peerId = randomHex(16);
        prefs.edit()
                .putString(KEY_PEER_ID, peerId)
                .putBoolean(KEY_HAS_IDENTITY, true)
                .apply();
        return peerId;
    }

    /** Shell-only "import": fabricates a placeholder id, does not actually recover anything. */
    public String importPlaceholderIdentity(String pastedSeedPhrase) {
        String peerId = "imported-" + randomHex(8);
        prefs.edit()
                .putString(KEY_PEER_ID, peerId)
                .putBoolean(KEY_HAS_IDENTITY, true)
                .apply();
        return peerId;
    }

    public String getPeerId() {
        return prefs.getString(KEY_PEER_ID, "");
    }

    public String getNickname() { return prefs.getString(KEY_NICKNAME, ""); }
    public void setNickname(String v) { prefs.edit().putString(KEY_NICKNAME, v).apply(); }

    public String getBio() { return prefs.getString(KEY_BIO, ""); }
    public void setBio(String v) { prefs.edit().putString(KEY_BIO, v).apply(); }

    public String getStatus() { return prefs.getString(KEY_STATUS, "online"); }
    public void setStatus(String v) { prefs.edit().putString(KEY_STATUS, v).apply(); }

    public int getAccentColor() { return prefs.getInt(KEY_ACCENT, 0xFF4FD1C5); }
    public void setAccentColor(int v) { prefs.edit().putInt(KEY_ACCENT, v).apply(); }

    public boolean isRelayEnabled() { return prefs.getBoolean(KEY_RELAY_ENABLED, false); }
    public void setRelayEnabled(boolean v) { prefs.edit().putBoolean(KEY_RELAY_ENABLED, v).apply(); }

    public boolean isRelayWifiOnly() { return prefs.getBoolean(KEY_RELAY_WIFI_ONLY, true); }
    public void setRelayWifiOnly(boolean v) { prefs.edit().putBoolean(KEY_RELAY_WIFI_ONLY, v).apply(); }

    public boolean isRelayChargingOnly() { return prefs.getBoolean(KEY_RELAY_CHARGING_ONLY, true); }
    public void setRelayChargingOnly(boolean v) { prefs.edit().putBoolean(KEY_RELAY_CHARGING_ONLY, v).apply(); }

    public int getRelayMaxMb() { return prefs.getInt(KEY_RELAY_MAX_MB, 500); }
    public void setRelayMaxMb(int v) { prefs.edit().putInt(KEY_RELAY_MAX_MB, v).apply(); }

    public int getRelayMinBattery() { return prefs.getInt(KEY_RELAY_MIN_BATTERY, 20); }
    public void setRelayMinBattery(int v) { prefs.edit().putInt(KEY_RELAY_MIN_BATTERY, v).apply(); }

    private static String randomHex(int numBytes) {
        SecureRandom random = new SecureRandom();
        byte[] buf = new byte[numBytes];
        random.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
