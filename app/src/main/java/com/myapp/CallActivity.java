package com.myapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.List;

public class CallActivity extends Activity implements SocketManager.CallListener {
    private static final int PERMISSION_REQUEST = 41;
    private WebRtcClient rtc;
    private SocketManager socket;
    private TextView status;
    private Button mic, camera, switchCamera, accept, end;
    private SurfaceViewRenderer localRenderer, remoteRenderer;
    private boolean incoming, accepted, ending, micOn = true, cameraOn = true;
    private boolean remoteDescriptionSet;
    private String target = "", offer = "";
    private final List<IceCandidate> pendingCandidates = new ArrayList<>();

    private abstract static class SdpCallback implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription description) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String error) { }
        @Override public void onSetFailure(String error) { }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_call);
        status = findViewById(R.id.callStatus);
        mic = findViewById(R.id.buttonMicrophone);
        camera = findViewById(R.id.buttonCamera);
        switchCamera = findViewById(R.id.buttonSwitchCamera);
        accept = findViewById(R.id.buttonAcceptCall);
        end = findViewById(R.id.buttonEndCall);
        localRenderer = findViewById(R.id.localVideoView);
        remoteRenderer = findViewById(R.id.remoteVideoView);

        socket = SocketManager.getInstance();
        socket.setCallListener(this);
        incoming = getIntent().getBooleanExtra("incoming", false);
        target = getIntent().getStringExtra("targetSocketId");
        offer = getIntent().getStringExtra("remoteSdp");
        if (target == null) target = "";
        if (offer == null) offer = "";

        accept.setVisibility(incoming ? View.VISIBLE : View.GONE);
        accept.setEnabled(false);
        end.setText(incoming ? "Reject call" : "End call");
        mic.setOnClickListener(v -> toggleMic());
        camera.setOnClickListener(v -> toggleCamera());
        switchCamera.setOnClickListener(v -> { if (rtc != null) rtc.switchCamera(); });
        accept.setOnClickListener(v -> acceptCall());
        end.setOnClickListener(v -> endCall());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST);
        } else {
            initializeCall();
        }
    }

    private void initializeCall() {
        try {
            rtc = new WebRtcClient();
            rtc.setIceCandidateListener(c -> {
                if (!target.isEmpty()) socket.sendIceCandidate(target, c);
            });
            rtc.setConnectionListener(s -> runOnUiThread(() -> {
                if ("CONNECTED".equals(s) || "COMPLETED".equals(s)) status.setText("Video call connected");
                else if ("DISCONNECTED".equals(s)) status.setText("Network weak — trying to reconnect…");
                else if ("FAILED".equals(s)) status.setText("Connection failed — check internet or TURN availability");
                else status.setText("Call connection: " + s);
            }));
            rtc.initialize(this, localRenderer, remoteRenderer);
            mic.setEnabled(true);
            camera.setEnabled(true);
            switchCamera.setEnabled(true);
            if (incoming) {
                status.setText("Incoming video call — tap Accept");
                accept.setEnabled(!offer.isEmpty());
            } else {
                status.setText("Calling… waiting for the other person");
                makeOffer();
            }
        } catch (Exception e) {
            status.setText("Camera or microphone could not start");
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
            accept.setEnabled(false);
        }
    }

    private void makeOffer() {
        rtc.createOffer(new SdpCallback() {
            @Override public void onCreateSuccess(SessionDescription description) {
                rtc.setLocalDescription(new SdpCallback() {
                    @Override public void onSetSuccess() {
                        socket.sendOffer(target, description.description);
                        show("Calling… invitation sent");
                    }
                    @Override public void onSetFailure(String error) { show("Could not prepare call: " + error); }
                }, description);
            }
            @Override public void onCreateFailure(String error) { show("Could not create video call: " + error); }
        }, new MediaConstraints());
    }

    private void acceptCall() {
        if (rtc == null || offer.isEmpty()) {
            show("Call is not ready. Please ask the caller to call again.");
            return;
        }
        accept.setEnabled(false);
        status.setText("Joining video call…");
        SessionDescription remote = new SessionDescription(SessionDescription.Type.OFFER, offer);
        rtc.setRemoteDescription(new SdpCallback() {
            @Override public void onSetSuccess() {
                remoteDescriptionSet = true;
                drainCandidates();
                rtc.createAnswer(new SdpCallback() {
                    @Override public void onCreateSuccess(SessionDescription answer) {
                        rtc.setLocalDescription(new SdpCallback() {
                            @Override public void onSetSuccess() {
                                socket.sendAnswer(target, answer.description);
                                accepted = true;
                                show("Video call connected — waiting for video");
                            }
                            @Override public void onSetFailure(String error) { show("Could not send answer: " + error); }
                        }, answer);
                    }
                    @Override public void onCreateFailure(String error) { show("Could not answer call: " + error); }
                }, new MediaConstraints());
            }
            @Override public void onSetFailure(String error) {
                show("Could not join call: " + error);
                accept.setEnabled(true);
            }
        }, remote);
    }

    @Override public void onAnswer(String sdp) {
        if (rtc == null) return;
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        rtc.setRemoteDescription(new SdpCallback() {
            @Override public void onSetSuccess() {
                remoteDescriptionSet = true;
                accepted = true;
                drainCandidates();
                show("Video call connected — waiting for video");
            }
            @Override public void onSetFailure(String error) { show("Answer error: " + error); }
        }, answer);
    }

    @Override public void onIceCandidate(IceCandidate candidate) {
        if (rtc == null || !remoteDescriptionSet) pendingCandidates.add(candidate);
        else rtc.addIceCandidate(candidate);
    }

    private void drainCandidates() {
        if (rtc == null) return;
        for (IceCandidate candidate : pendingCandidates) rtc.addIceCandidate(candidate);
        pendingCandidates.clear();
    }

    @Override public void onCallEnded() {
        show("The other person ended the call");
        runOnUiThread(this::finish);
    }

    @Override public void onCallRejected() {
        show("The other person rejected the call");
        runOnUiThread(this::finish);
    }

    private void toggleMic() {
        micOn = !micOn;
        if (rtc != null) rtc.setMicrophoneEnabled(micOn);
        mic.setText(micOn ? "Mute mic" : "Unmute mic");
    }

    private void toggleCamera() {
        cameraOn = !cameraOn;
        if (rtc != null) rtc.setCameraEnabled(cameraOn);
        camera.setText(cameraOn ? "Turn camera off" : "Turn camera on");
        if (!cameraOn) localRenderer.setVisibility(View.INVISIBLE);
        else localRenderer.setVisibility(View.VISIBLE);
    }

    private void endCall() {
        if (ending) return;
        ending = true;
        if (!target.isEmpty()) {
            if (incoming && !accepted) socket.sendCallReject(target);
            else socket.sendCallEnd(target);
        }
        finish();
    }

    private void show(String message) { runOnUiThread(() -> status.setText(message)); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == PERMISSION_REQUEST && grants.length >= 2
                && grants[0] == PackageManager.PERMISSION_GRANTED
                && grants[1] == PackageManager.PERMISSION_GRANTED) {
            initializeCall();
        } else {
            status.setText("Camera and microphone permissions are required for video calls");
            accept.setEnabled(false);
            mic.setEnabled(false);
            camera.setEnabled(false);
            switchCamera.setEnabled(false);
        }
    }

    @Override protected void onDestroy() {
        socket.setCallListener(null);
        if (rtc != null) rtc.close();
        super.onDestroy();
    }
}
