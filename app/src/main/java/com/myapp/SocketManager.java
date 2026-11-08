package com.myapp;

import android.util.Log;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;
import java.net.URISyntaxException;

public class SocketManager {
    private static final String TAG = "PrivateCallSocket";
    private Socket socket;

    public void connect(String serverUrl) throws URISyntaxException {
        if (socket != null && socket.connected()) return;
        socket = IO.socket(serverUrl);
        socket.on(Socket.EVENT_CONNECT, args -> Log.d(TAG, "Connected to signaling server"));
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> Log.e(TAG, "Socket connection error"));
        socket.on(Socket.EVENT_DISCONNECT, args -> Log.d(TAG, "Disconnected"));
        socket.connect();
    }

    public void sendChatMessage(String roomId, String message) {
        if (socket == null || !socket.connected()) {
            Log.w(TAG, "Not connected; message was not sent");
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("roomId", roomId);
            payload.put("message", message);
            socket.emit("chat:message", payload);
        } catch (Exception e) {
            Log.e(TAG, "Could not create message payload", e);
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
    }
}
