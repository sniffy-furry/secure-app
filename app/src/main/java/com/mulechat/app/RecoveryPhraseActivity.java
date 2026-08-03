package com.mulechat.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mulechat.app.data.NulChatRepository;
import com.mulechat.app.identity.Identity;
import com.mulechat.app.identity.IdentityGenerator;
import com.mulechat.app.identity.SeedPhrase;
import com.mulechat.app.storage.AppDatabase;

import java.util.List;

/**
 * Shows the 24-word recovery phrase exactly once and requires an explicit
 * "I've written this down" confirmation before the identity is actually
 * saved. Only the words travel here from OnboardingActivity -- the
 * identity (including the private key) is recomputed deterministically
 * from them on confirm, rather than passed through an Intent extra.
 */
public class RecoveryPhraseActivity extends AppCompatActivity {

    public static final String EXTRA_WORDS = "extra_words";

    private List<String> words;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recovery_phrase);

        words = getIntent().getStringArrayListExtra(EXTRA_WORDS);
        if (words == null || words.size() != 24) {
            Toast.makeText(this, R.string.onboarding_error_generic, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        GridLayout grid = findViewById(R.id.grid_words);
        for (int i = 0; i < words.size(); i++) {
            TextView wordView = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_recovery_word, grid, false);
            wordView.setText((i + 1) + ". " + words.get(i));
            grid.addView(wordView);
        }

        CheckBox confirmBox = findViewById(R.id.checkbox_confirm_saved);
        View continueButton = findViewById(R.id.btn_continue);
        confirmBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                continueButton.setEnabled(isChecked));

        continueButton.setOnClickListener(v -> saveAndContinue());
    }

    private void saveAndContinue() {
        findViewById(R.id.btn_continue).setEnabled(false);
        new Thread(() -> {
            try {
                byte[] entropy = SeedPhrase.toEntropy(words);
                Identity identity = IdentityGenerator.fromSeed(entropy);
                NulChatRepository repo = AppDatabase.getOrOpen(getApplicationContext());
                repo.saveIdentity(identity, "");
                repo.ensurePreKeysExist(identity);
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.onboarding_error_generic, Toast.LENGTH_LONG).show();
                    findViewById(R.id.btn_continue).setEnabled(true);
                });
            }
        }).start();
    }
}
