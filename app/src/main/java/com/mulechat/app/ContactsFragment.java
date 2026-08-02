package com.mulechat.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mulechat.app.adapter.ContactAdapter;
import com.mulechat.app.model.Contact;
import com.mulechat.app.util.ContactRepository;

import java.util.List;
import java.util.Random;

public class ContactsFragment extends Fragment {

    private static final int[] ACCENT_PALETTE = {
            0xFF4FD1C5, // signal teal
            0xFFE8A33D, // warn amber
            0xFFE8636B, // danger red
            0xFF8B8FE8, // soft violet
            0xFF6FBF73  // muted green
    };

    private ContactAdapter adapter;
    private TextView emptyView;
    private List<Contact> contacts;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        contacts = ContactRepository.getInstance().getAll();
        emptyView = view.findViewById(R.id.text_contacts_empty);

        RecyclerView recycler = view.findViewById(R.id.recycler_contacts);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContactAdapter(contacts, this::openConversation);
        recycler.setAdapter(adapter);

        view.findViewById(R.id.fab_add_contact).setOnClickListener(v -> showAddContactDialog());

        updateEmptyState();
    }

    private void openConversation(Contact contact) {
        Intent intent = new Intent(requireContext(), ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_NICKNAME, contact.nickname);
        intent.putExtra(ConversationActivity.EXTRA_PEER_HINT, contact.peerIdHint);
        startActivity(intent);
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_contact, null);
        EditText inviteInput = dialogView.findViewById(R.id.input_invite_string);
        EditText nicknameInput = dialogView.findViewById(R.id.input_contact_nickname);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_contact_title)
                .setView(dialogView)
                .setPositiveButton(R.string.add_contact_confirm, (dialog, which) -> {
                    String invite = inviteInput.getText().toString().trim();
                    String nickname = nicknameInput.getText().toString().trim();
                    if (nickname.isEmpty()) nickname = "unnamed";
                    if (invite.isEmpty()) invite = randomPeerHint();

                    int accent = ACCENT_PALETTE[new Random().nextInt(ACCENT_PALETTE.length)];
                    ContactRepository.getInstance().add(new Contact(invite, nickname, accent));
                    adapter.notifyItemInserted(contacts.size() - 1);
                    updateEmptyState();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateEmptyState() {
        emptyView.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String randomPeerHint() {
        String chars = "0123456789abcdef";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
