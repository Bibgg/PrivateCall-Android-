package com.myapp;

import android.content.Context;
import android.util.Log;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import java.util.Arrays;
import java.util.List;

public class WebRtcClient {
    private static final String TAG = "PrivateCallWebRTC";
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private IceCandidateListener iceCandidateListener;
    public interface IceCandidateListener { void onCandidate(org.webrtc.IceCandidate candidate); }
    public void setIceCandidateListener(IceCandidateListener listener) { iceCandidateListener = listener; }

    public void initialize(Context context) {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions());

        factory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .createPeerConnectionFactory();

        List<PeerConnection.IceServer> iceServers = Arrays.asList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        );
        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(iceServers);

        peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) {
                Log.d(TAG, "Signaling: " + state);
            }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                Log.d(TAG, "ICE: " + state);
            }
            @Override public void onIceConnectionReceivingChange(boolean receiving) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
            @Override public void onIceCandidate(org.webrtc.IceCandidate candidate) {
                // ICE signaling is the next integration step.
                Log.d(TAG, "Local ICE candidate gathered");
            }
            @Override public void onIceCandidatesRemoved(org.webrtc.IceCandidate[] candidates) { }
            @Override public void onAddStream(org.webrtc.MediaStream stream) { }
            @Override public void onRemoveStream(org.webrtc.MediaStream stream) { }
            @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onAddTrack(org.webrtc.RtpReceiver receiver,
                                             org.webrtc.MediaStream[] streams) {
                if (receiver.track() instanceof AudioTrack) {
                    ((AudioTrack) receiver.track()).setEnabled(true);
                }
            }
        });

        if (peerConnection == null) {
            throw new IllegalStateException("Could not create WebRTC peer connection");
        }

        audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("privatecall-microphone", audioSource);
        localAudioTrack.setEnabled(true);
        peerConnection.addTrack(localAudioTrack, Arrays.asList("privatecall-audio-stream"));
    }

    public void addIceCandidate(org.webrtc.IceCandidate candidate) { if (peerConnection != null) peerConnection.addIceCandidate(candidate); }

    public void setMicrophoneEnabled(boolean enabled) {
        if (localAudioTrack != null) localAudioTrack.setEnabled(enabled);
    }

    public void createOffer(SdpObserver observer) {
        if (peerConnection != null) peerConnection.createOffer(observer, new MediaConstraints());
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
            peerConnection.dispose();
            peerConnection = null;
        }
        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
    }
}
