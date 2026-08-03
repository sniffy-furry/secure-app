package com.mulechat.app;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mulechat.app.storage.AppDatabase;
import com.mulechat.app.util.IdentityStore;

import java.util.Arrays;
import java.util.List;

public class ProfileFragment extends Fragment {

    private static final List<Integer> ACCENTS = Arrays.asList(
            0xFF4FD1C5, 0xFFE8A33D, 0xFFE8636B, 0xFF8B8FE8, 0xFF6FBF73
    );

    private IdentityStore identityStore;
    private int selectedAccent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        identityStore = new IdentityStore(requireContext());

        TextView peerIdView = view.findViewById(R.id.text_peer_id);
        peerIdView.setText("…");
        new Thread(() -> {
            try {
                String peerId = AppDatabase.getOrOpen(requireContext().getApplicationContext())
                        .getIdentity().peerId;
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) peerIdView.setText(peerId);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) peerIdView.setText("—");
                });
            }
        }).start();

        EditText nicknameInput = view.findViewById(R.id.input_nickname);
        nicknameInput.setText(identityStore.getNickname());

        EditText bioInput = view.findViewById(R.id.input_bio);
        bioInput.setText(identityStore.getBio());

        Spinner statusSpinner = view.findViewById(R.id.spinner_status);
        String[] statuses = getResources().getStringArray(R.array.status_options);
        int currentStatusIndex = Arrays.asList(statuses).indexOf(identityStore.getStatus());
        statusSpinner.setSelection(Math.max(currentStatusIndex, 0));

        selectedAccent = identityStore.getAccentColor();
        buildAccentSwatches(view);

        view.findViewById(R.id.btn_save_profile).setOnClickListener(v -> {
            identityStore.setNickname(nicknameInput.getText().toString().trim());
            identityStore.setBio(bioInput.getText().toString().trim());
            identityStore.setStatus((String) statusSpinner.getSelectedItem());
            identityStore.setAccentColor(selectedAccent);
            Toast.makeText(requireContext(), R.string.profile_saved_toast, Toast.LENGTH_SHORT).show();
        });
    }

    private void buildAccentSwatches(View root) {
        LinearLayout row = root.findViewById(R.id.accent_swatch_row);
        row.removeAllViews();
        int sizePx = dp(36);
        int marginPx = dp(10);

        for (int color : ACCENTS) {
            View swatch = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMarginEnd(marginPx);
            swatch.setLayoutParams(params);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            if (color == selectedAccent) {
                bg.setStroke(dp(3), 0xFFFFFFFF);
            }
            swatch.setBackground(bg);

            swatch.setOnClickListener(v -> {
                selectedAccent = color;
                buildAccentSwatches(root); // cheap redraw to move the selection ring
            });

            row.addView(swatch);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
