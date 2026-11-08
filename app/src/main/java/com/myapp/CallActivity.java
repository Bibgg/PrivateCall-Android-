package com.myapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class CallActivity extends Activity {
    private static final int PERMISSION_REQUEST = 41;
    private WebRtcClient webRtcClient;
    private TextView status;
    private Button microphoneButton;
    private boolean microphoneEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        status = findViewById(R.id.callStatus);
        microphoneButton = findViewById(R.id.buttonMicrophone);
        Button endCall = findViewById(R.id.buttonEndCall);

        microphoneButton.setOnClickListener(v -> toggleMicrophone());
        endCall.setOnClickListener(v -> finish());

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST);
        } else {
            initializeAudio();
        }
    }

    private void initializeAudio() {
        try {
            webRtcClient = new WebRtcClient();
            webRtcClient.initialize(this);
            status.setText("Microphone ready. WebRTC audio engine initialized.");
            microphoneButton.setEnabled(true);
        } catch (Exception e) {
            status.setText("Audio initialization failed");
            microphoneButton.setEnabled(false);
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void toggleMicrophone() {
        microphoneEnabled = !microphoneEnabled;
        if (webRtcClient != null) webRtcClient.setMicrophoneEnabled(microphoneEnabled);
        microphoneButton.setText(microphoneEnabled ? "Mute microphone" : "Unmute microphone");
        status.setText(microphoneEnabled ? "Microphone is on" : "Microphone is muted");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeAudio();
        } else {
            status.setText("Microphone permission is required for audio calls");
            microphoneButton.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (webRtcClient != null) webRtcClient.close();
        super.onDestroy();
    }
}
