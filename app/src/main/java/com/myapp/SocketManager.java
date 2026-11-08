package com.myapp;

import android.util.Log;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;
import java.net.URISyntaxException;

public class SocketManager {
    private static final String TAG = "PrivateCallSocket";
    private Socket socket;
    private Listener listener;
    private String roomId;

    public interface Listener {
        void onStatus(String status);
        void onMessage(String message, String senderSocketId);
        void onError(String error);
    }

    public void connect(String serverUrl, String roomId, Listener listener) throws URISyntaxException {
        this.listener = listener;
        this.roomId = roomId;

        if (socket != null) {
            if (socket.connected()) {
                joinRoom();
                return;
            }
            socket.off();
            socket.disconnect();
            socket = null;
        }

        IO.Options options = new IO.Options();
        options.reconnection = true;
        options.reconnectionAttempts = Integer.MAX_VALUE;
        options.reconnectionDelay = 1000;
        options.timeout = 20000;

        socket = IO.socket(serverUrl, options);

        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Connected to signaling server: " + socket.id());
            notifyStatus("Connected — joining chat room…");
            joinRoom();
        });

        socket.on("room:joined", args -> notifyStatus("Online — room joined"));

        socket.on("room:user-joined", args -> notifyStatus("Another user joined the test room"));

        socket.on("chat:message", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                JSONObject data = (JSONObject) args[0];
                String message = data.optString("message", "");
                String sender = data.optString("senderSocketId", "");
                if (!message.isEmpty() && listener != null) {
                    listener.onMessage(message, sender);
                }
            }
        });

        socket.on("server:error", args -> {
            String message = "Server error";
            if (args.length > 0 && args[0] instanceof JSONObject) {
                message = ((JSONObject) args[0]).optString("message", message);
            }
            notifyError(message);
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            String detail = args.length > 0 ? String.valueOf(args[0]) : "Unknown connection error";
            Log.e(TAG, "Socket connection error: " + detail);
            notifyStatus("Connection error — retrying");
            notifyError(detail);
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            String reason = args.length > 0 ? String.valueOf(args[0]) : "disconnected";
            Log.d(TAG, "Disconnected: " + reason);
            notifyStatus("Disconnected — reconnecting…");
        });

        notifyStatus("Connecting to server…");
        socket.connect();
    }

    private void joinRoom() {
        if (socket == null || !socket.connected() || roomId == null) return;
        JSONObject payload = new JSONObject();
        try {
            payload.put("roomId", roomId);
            socket.emit("room:join", payload);
        } catch (Exception e) {
            Log.e(TAG, "Could not join room", e);
            notifyError("Could not join chat room");
        }
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void sendChatMessage(String message) {
        if (!isConnected() || roomId == null) {
            notifyError("Not connected to the server yet");
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("roomId", roomId);
            payload.put("message", message);
            socket.emit("chat:message", payload);
        } catch (Exception e) {
            Log.e(TAG, "Could not create message payload", e);
            notifyError("Could not send message");
        }
    }

    public String getSocketId() {
        return socket == null ? "" : socket.id();
    }

    private void notifyStatus(String status) {
        if (listener != null) listener.onStatus(status);
    }

    private void notifyError(String error) {
        if (listener != null) listener.onError(error);
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
        listener = null;
    }
}
