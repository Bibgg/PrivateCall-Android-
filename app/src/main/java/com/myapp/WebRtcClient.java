package com.myapp;

import android.content.Context;
import android.util.Log;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import java.util.Arrays;
import java.util.List;

public class WebRtcClient {
    private static final String TAG = "PrivateCallWebRTC";
    private final EglBase eglBase = EglBase.create();
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    public void initialize(Context context, SurfaceViewRenderer remoteRenderer) {
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        factory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .createPeerConnectionFactory();

        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setEnableHardwareScaler(true);
        remoteRenderer.setMirror(false);

        List<PeerConnection.IceServer> iceServers = Arrays.asList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        );
        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(iceServers);

        peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                Log.d(TAG, "ICE state: " + state);
            }
            @Override public void onIceConnectionReceivingChange(boolean receiving) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
            @Override public void onIceCandidate(org.webrtc.IceCandidate candidate) {
                // TODO: Send candidate to the other peer through Socket.io signaling.
            }
            @Override public void onIceCandidatesRemoved(org.webrtc.IceCandidate[] candidates) { }
            @Override public void onAddStream(org.webrtc.MediaStream stream) { }
            @Override public void onRemoveStream(org.webrtc.MediaStream stream) { }
            @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onAddTrack(org.webrtc.RtpReceiver receiver,
                                             org.webrtc.MediaStream[] streams) { }
        });

        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection creation failed");
        }
    }

    public void createOffer(SdpObserver observer) {
        if (peerConnection == null) return;
        peerConnection.createOffer(observer, new PeerConnection.MediaConstraints());
    }

    public void setLocalDescription(SdpObserver observer, SessionDescription description) {
        if (peerConnection != null) peerConnection.setLocalDescription(observer, description);
    }

    public void setRemoteDescription(SdpObserver observer, SessionDescription description) {
        if (peerConnection != null) peerConnection.setRemoteDescription(observer, description);
    }

    public void close() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        eglBase.release();
    }
}
