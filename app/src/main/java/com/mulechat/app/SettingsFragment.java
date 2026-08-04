package com.mulechat.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import com.mulechat.app.backup.BackupExporter;
import com.mulechat.app.backup.BackupScheduler;
import com.mulechat.app.crypto.X3DHSelfTest;
import com.mulechat.app.data.NulChatRepository;
import com.mulechat.app.identity.Identity;
import com.mulechat.app.storage.AppDatabase;
import com.mulechat.app.util.IdentityStore;

import java.io.InputStream;

public class SettingsFragment extends Fragment {

    private IdentityStore identityStore;
    private SwitchMaterial backupEnabled;
    private TextView backupFolderStatus;

    private final ActivityResultLauncher<Uri> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                requireContext().getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                identityStore.setBackupFolderUri(uri.toString());
                updateBackupFolderStatus();
                BackupScheduler.rescheduleFromSettings(requireContext(), backupEnabled.isChecked());
            });

    private final ActivityResultLauncher<String[]> restorePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) runRestore(uri);
            });

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

        // ---- Backup ----

        backupEnabled = view.findViewById(R.id.switch_backup_enabled);
        backupFolderStatus = view.findViewById(R.id.text_backup_folder_status);
        backupEnabled.setChecked(identityStore.isBackupEnabled());
        updateBackupFolderStatus();

        backupEnabled.setOnCheckedChangeListener((button, checked) -> {
            identityStore.setBackupEnabled(checked);
            BackupScheduler.rescheduleFromSettings(requireContext(), checked);
        });

        view.findViewById(R.id.btn_choose_backup_folder).setOnClickListener(v -> folderPickerLauncher.launch(null));

        view.findViewById(R.id.btn_restore_backup).setOnClickListener(v ->
                restorePickerLauncher.launch(new String[]{"*/*"}));

        view.findViewById(R.id.btn_run_x3dh_selftest).setOnClickListener(v -> new Thread(() -> {
            String title;
            String message;
            try {
                message = X3DHSelfTest.run();
                title = getString(R.string.settings_debug_selftest_pass_title);
            } catch (Exception e) {
                message = String.valueOf(e.getMessage());
                title = getString(R.string.settings_debug_selftest_fail_title);
            }
            String finalTitle = title;
            String finalMessage = message;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                        .setTitle(finalTitle)
                        .setMessage(finalMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        }).start());
    }

    private void updateBackupFolderStatus() {
        String uri = identityStore.getBackupFolderUri();
        backupFolderStatus.setText(uri == null
                ? getString(R.string.settings_backup_no_folder)
                : getString(R.string.settings_backup_folder_set, Uri.parse(uri).getLastPathSegment()));
    }

    private void runRestore(Uri fileUri) {
        new Thread(() -> {
            String title;
            String message;
            try (InputStream in = requireContext().getContentResolver().openInputStream(fileUri)) {
                if (in == null) throw new java.io.IOException("Could not open the chosen file");
                byte[] encrypted = readAll(in);

                NulChatRepository repo = AppDatabase.getOrOpen(requireContext());
                Identity identity = repo.getIdentity();
                if (identity == null) throw new IllegalStateException("No identity yet on this device");

                BackupExporter.restoreEncryptedBackup(encrypted, repo, identity);
                title = getString(R.string.settings_backup_restore_ok_title);
                message = getString(R.string.settings_backup_restore_ok_message);
            } catch (Exception e) {
                title = getString(R.string.settings_backup_restore_fail_title);
                message = String.valueOf(e.getMessage());
            }
            String finalTitle = title;
            String finalMessage = message;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                        .setTitle(finalTitle)
                        .setMessage(finalMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        }).start();
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) out.write(chunk, 0, read);
        return out.toByteArray();
    }

    private static int parseIntOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
