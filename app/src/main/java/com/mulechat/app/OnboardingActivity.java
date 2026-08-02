package com.mulechat.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.mulechat.app.util.IdentityStore;

public class OnboardingActivity extends AppCompatActivity {

    private IdentityStore identityStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        identityStore = new IdentityStore(this);

        // Skip straight to the app if a placeholder identity already exists
        // (shell-only check -- see IdentityStore for what "identity" means here).
        if (identityStore.hasIdentity()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        findViewById(R.id.btn_create).setOnClickListener(v -> {
            identityStore.createPlaceholderIdentity();
            goToMain();
        });

        findViewById(R.id.btn_import).setOnClickListener(v -> showImportDialog());
    }

    private void showImportDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_import_identity, null);
        EditText input = dialogView.findViewById(R.id.input_seed_phrase);

        new AlertDialog.Builder(this)
                .setTitle(R.string.import_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.import_dialog_confirm, (dialog, which) -> {
                    identityStore.importPlaceholderIdentity(input.getText().toString());
                    goToMain();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
