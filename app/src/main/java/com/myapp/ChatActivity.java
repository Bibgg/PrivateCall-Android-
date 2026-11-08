package com.myapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends Activity {
    private static final String SERVER_URL = "https://privatecall-server.onrender.com";
    // Temporary shared room for testing only. Authentication and private rooms come later.
    private static final String TEST_ROOM_ID = "privatecall-test-room";

    private final List<String> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private SocketManager socketManager;
    private TextView connectionStatus;
    private RecyclerView recycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recycler = findViewById(R.id.recyclerMessages);
        EditText input = findViewById(R.id.editMessage);
        Button send = findViewById(R.id.buttonSend);
        Button call = findViewById(R.id.buttonCall);
        connectionStatus = findViewById(R.id.connectionStatus);

        adapter = new ChatAdapter(messages);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        messages.add("Welcome to PrivateCall. Connecting to server…");
        adapter.notifyItemInserted(0);

        socketManager = new SocketManager();
        connectToServer();

        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;

            if (!socketManager.isConnected()) {
                Toast.makeText(this, "Server is not connected yet", Toast.LENGTH_SHORT).show();
                return;
            }

            socketManager.sendChatMessage(text);
            input.setText("");
        });

        call.setOnClickListener(v ->
                Toast.makeText(this, "Calling will be connected after chat signaling is tested.", Toast.LENGTH_LONG).show()
        );
    }

    private void connectToServer() {
        try {
            socketManager.connect(SERVER_URL, TEST_ROOM_ID, new SocketManager.Listener() {
                @Override
                public void onStatus(String status) {
                    runOnUiThread(() -> connectionStatus.setText(status));
                }

                @Override
                public void onMessage(String message, String senderSocketId) {
                    runOnUiThread(() -> {
                        String currentSocketId = socketManager.getSocketId();
                        String prefix = senderSocketId.equals(currentSocketId) ? "You: " : "Other: ";
                        messages.add(prefix + message);
                        adapter.notifyItemInserted(messages.size() - 1);
                        recycler.scrollToPosition(messages.size() - 1);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() ->
                            Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show()
                    );
                }
            });
        } catch (URISyntaxException e) {
            connectionStatus.setText("Invalid server URL");
            Toast.makeText(this, "Could not connect to server", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (socketManager != null) socketManager.disconnect();
        super.onDestroy();
    }
}
