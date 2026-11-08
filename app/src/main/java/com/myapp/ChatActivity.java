package com.myapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends Activity {
    private final List<String> messages = new ArrayList<>();
    private ChatAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        RecyclerView recycler = findViewById(R.id.recyclerMessages);
        EditText input = findViewById(R.id.editMessage);
        Button send = findViewById(R.id.buttonSend);
        Button call = findViewById(R.id.buttonCall);

        adapter = new ChatAdapter(messages);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        messages.add("Welcome to PrivateCall. Connect a signaling server to send messages.");
        adapter.notifyItemInserted(0);

        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            messages.add("You: " + text);
            adapter.notifyItemInserted(messages.size() - 1);
            recycler.scrollToPosition(messages.size() - 1);
            input.setText("");
            // TODO: Emit the message through SocketManager after configuring your server URL.
        });

        call.setOnClickListener(v -> startActivity(new Intent(this, CallActivity.class)));
    }
}
