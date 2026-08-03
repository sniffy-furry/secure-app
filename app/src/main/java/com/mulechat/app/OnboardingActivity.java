package com.mulechat.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mulechat.app.data.NulChatRepository;
import com.mulechat.app.identity.Identity;
import com.mulechat.app.identity.IdentityGenerator;
import com.mulechat.app.identity.SeedPhrase;
import com.mulechat.app.storage.AppDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Real identity flow: generates (or restores) an Ed25519 keypair via
 * IdentityGenerator, shows/confirms the 24-word recovery phrase for a new
 * identity, and persists it to the encrypted DB via NulChatRepository.
 * Nothing here is placeholder data anymore -- see RecoveryPhraseActivity
 * for where the phrase is actually shown.
 */
public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        setButtonsEnabled(false);

        // Opening the DB touches disk + Keystore -- never on the UI thread.
        new Thread(() -> {
            try {
                NulChatRepository repo = AppDatabase.getOrOpen(getApplicationContext());
                Identity existing = repo.getIdentity();
                runOnUiThread(() -> {
                    if (existing != null) {
                        goToMain();
                    } else {
                        setButtonsEnabled(true);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.onboarding_error_generic, Toast.LENGTH_LONG).show();
                    setButtonsEnabled(true);
                });
            }
        }).start();

        findViewById(R.id.btn_create).setOnClickListener(v -> createNewIdentity());
        findViewById(R.id.btn_import).setOnClickListener(v -> showImportDialog());
    }

    private void setButtonsEnabled(boolean enabled) {
        findViewById(R.id.btn_create).setEnabled(enabled);
        findViewById(R.id.btn_import).setEnabled(enabled);
    }

    private void createNewIdentity() {
        setButtonsEnabled(false);
        new Thread(() -> {
            try {
                SeedPhrase.Seed seed = SeedPhrase.generate();
                Identity identity = IdentityGenerator.fromSeed(seed.entropy);
                // Not saved yet -- only persisted after the user confirms
                // they've written the phrase down, in RecoveryPhraseActivity.
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, RecoveryPhraseActivity.class);
                    intent.putStringArrayListExtra(
                            RecoveryPhraseActivity.EXTRA_WORDS, new ArrayList<>(seed.words));
                    startActivity(intent);
                    setButtonsEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.onboarding_error_generic, Toast.LENGTH_LONG).show();
                    setButtonsEnabled(true);
                });
            }
        }).start();
    }

    private void showImportDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_import_identity, null);
        EditText input = dialogView.findViewById(R.id.input_seed_phrase);

        new AlertDialog.Builder(this)
                .setTitle(R.string.import_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.import_dialog_confirm, (dialog, which) ->
                        restoreFromPhrase(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void restoreFromPhrase(String pastedText) {
        List<String> words = new ArrayList<>(Arrays.asList(pastedText.trim().split("\\s+")));

        setButtonsEnabled(false);
        new Thread(() -> {
            try {
                byte[] entropy = SeedPhrase.toEntropy(words); // throws IllegalArgumentException if invalid
                Identity identity = IdentityGenerator.fromSeed(entropy);
                NulChatRepository repo = AppDatabase.getOrOpen(getApplicationContext());
                repo.saveIdentity(identity, "");
                runOnUiThread(this::goToMain);
            } catch (IllegalArgumentException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.import_error_invalid, Toast.LENGTH_LONG).show();
                    setButtonsEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.onboarding_error_generic, Toast.LENGTH_LONG).show();
                    setButtonsEnabled(true);
                });
            }
        }).start();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
