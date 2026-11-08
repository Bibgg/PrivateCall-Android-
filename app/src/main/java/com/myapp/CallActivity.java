package com.myapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.webrtc.SurfaceViewRenderer;

public class CallActivity extends Activity {
    private static final int PERMISSION_REQUEST = 41;
    private WebRtcClient webRtcClient;
    private SurfaceViewRenderer remoteRenderer;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        remoteRenderer = findViewById(R.id.remoteVideoView);
        status = findViewById(R.id.callStatus);
        Button endCall = findViewById(R.id.buttonEndCall);
        endCall.setOnClickListener(v -> finish());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST);
        } else {
            initializeWebRtc();
        }
    }

    private void initializeWebRtc() {
        try {
            webRtcClient = new WebRtcClient();
            webRtcClient.initialize(this, remoteRenderer);
            status.setText("WebRTC ready — signaling server required");
        } catch (Exception e) {
            status.setText("WebRTC initialization failed");
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults.length > 1
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            initializeWebRtc();
        } else {
            status.setText("Camera and microphone permissions are required");
        }
    }

    @Override
    protected void onDestroy() {
        if (remoteRenderer != null) remoteRenderer.release();
        if (webRtcClient != null) webRtcClient.close();
        super.onDestroy();
    }
}
