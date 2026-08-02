package com.mulechat.app;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mulechat.app.adapter.MessageAdapter;
import com.mulechat.app.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * DM view. Sending here only appends a bubble to an in-memory list -- there
 * is no real recipient, no encryption, and nothing leaves the device. See
 * the plan's wire-protocol section for what a real send path involves.
 */
public class ConversationActivity extends AppCompatActivity {

    public static final String EXTRA_NICKNAME = "extra_nickname";
    public static final String EXTRA_PEER_HINT = "extra_peer_hint";

    private final List<Message> messages = new ArrayList<>();
    private MessageAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        String nickname = getIntent().getStringExtra(EXTRA_NICKNAME);
        if (nickname == null) nickname = "Unknown";

        ((TextView) findViewById(R.id.text_conversation_title)).setText(nickname);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        emptyView = findViewById(R.id.text_conversation_empty);
        recycler = findViewById(R.id.recycler_messages);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(messages);
        recycler.setAdapter(adapter);
        updateEmptyState();

        EditText input = findViewById(R.id.input_message);
        findViewById(R.id.btn_send).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            appendMessage(text, true);
            input.setText("");

            Toast.makeText(this, "Not sent -- no networking wired up yet", Toast.LENGTH_SHORT).show();
        });
    }

    private void appendMessage(String text, boolean fromMe) {
        messages.add(new Message(text, fromMe, System.currentTimeMillis()));
        adapter.notifyItemInserted(messages.size() - 1);
        recycler.scrollToPosition(messages.size() - 1);
        updateEmptyState();
    }

    private void updateEmptyState() {
        emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
