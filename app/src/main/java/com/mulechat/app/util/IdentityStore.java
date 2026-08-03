package com.mulechat.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local-only storage for profile display fields (nickname, bio, status,
 * accent color) and relay UI settings. NOT the cryptographic identity --
 * that's real now and lives in the encrypted DB, see
 * storage.AppDatabase / data.NulChatRepository.getIdentity(). This class
 * only ever held UI-layer prefs; it used to also fake a placeholder peer id
 * before onboarding was wired to real key generation.
 */
public class IdentityStore {
    private static final String PREFS = "mulechat_shell_prefs";
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
}
