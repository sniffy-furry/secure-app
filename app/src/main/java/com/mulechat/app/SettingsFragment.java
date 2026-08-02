package com.mulechat.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import com.mulechat.app.util.IdentityStore;

public class SettingsFragment extends Fragment {

    private IdentityStore identityStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        identityStore = new IdentityStore(requireContext());

        SwitchMaterial relayEnabled = view.findViewById(R.id.switch_relay_enabled);
        SwitchMaterial wifiOnly = view.findViewById(R.id.switch_relay_wifi_only);
        SwitchMaterial chargingOnly = view.findViewById(R.id.switch_relay_charging_only);
        EditText maxMbInput = view.findViewById(R.id.input_relay_max_mb);
        EditText minBatteryInput = view.findViewById(R.id.input_relay_min_battery);

        relayEnabled.setChecked(identityStore.isRelayEnabled());
        wifiOnly.setChecked(identityStore.isRelayWifiOnly());
        chargingOnly.setChecked(identityStore.isRelayChargingOnly());
        maxMbInput.setText(String.valueOf(identityStore.getRelayMaxMb()));
        minBatteryInput.setText(String.valueOf(identityStore.getRelayMinBattery()));

        view.findViewById(R.id.btn_save_settings).setOnClickListener(v -> {
            identityStore.setRelayEnabled(relayEnabled.isChecked());
            identityStore.setRelayWifiOnly(wifiOnly.isChecked());
            identityStore.setRelayChargingOnly(chargingOnly.isChecked());
            identityStore.setRelayMaxMb(parseIntOr(maxMbInput.getText().toString(), identityStore.getRelayMaxMb()));
            identityStore.setRelayMinBattery(parseIntOr(minBatteryInput.getText().toString(), identityStore.getRelayMinBattery()));
            Toast.makeText(requireContext(), R.string.profile_saved_toast, Toast.LENGTH_SHORT).show();
        });
    }

    private static int parseIntOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
