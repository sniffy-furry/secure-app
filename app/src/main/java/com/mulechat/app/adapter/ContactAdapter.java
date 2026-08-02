package com.mulechat.app.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mulechat.app.R;
import com.mulechat.app.model.Contact;

import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {

    public interface OnContactClickListener {
        void onContactClick(Contact contact);
    }

    private final List<Contact> contacts;
    private final OnContactClickListener listener;

    public ContactAdapter(List<Contact> contacts, OnContactClickListener listener) {
        this.contacts = contacts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact = contacts.get(position);
        holder.nickname.setText(contact.nickname);
        holder.peerHint.setText("peer:" + shorten(contact.peerIdHint));

        String initial = contact.nickname.isEmpty() ? "?" : contact.nickname.substring(0, 1).toUpperCase();
        holder.avatarInitial.setText(initial);

        GradientDrawable bg = (GradientDrawable) holder.avatarInitial.getBackground().mutate();
        bg.setColor(contact.accentColor);

        holder.itemView.setOnClickListener(v -> listener.onContactClick(contact));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    private static String shorten(String hint) {
        return hint.length() > 12 ? hint.substring(0, 12) + "…" : hint;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView avatarInitial;
        final TextView nickname;
        final TextView peerHint;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarInitial = itemView.findViewById(R.id.text_avatar_initial);
            nickname = itemView.findViewById(R.id.text_contact_nickname);
            peerHint = itemView.findViewById(R.id.text_contact_peer_hint);
        }
    }
}
