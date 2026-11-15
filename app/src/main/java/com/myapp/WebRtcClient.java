package com.myapp;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.Arrays;
import java.util.List;

public class WebRtcClient {
    private static final String TAG = "PrivateCallWebRTC";
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private CameraVideoCapturer cameraCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private EglBase eglBase;
    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;
    private IceCandidateListener iceCandidateListener;
    private ConnectionListener connectionListener;
    private boolean remoteVideoAttached;

    public interface IceCandidateListener { void onCandidate(org.webrtc.IceCandidate candidate); }
    public interface ConnectionListener { void onState(String state); }

    public void setIceCandidateListener(IceCandidateListener listener) { iceCandidateListener = listener; }
    public void setConnectionListener(ConnectionListener listener) { connectionListener = listener; }

    public void initialize(Context context, SurfaceViewRenderer localView, SurfaceViewRenderer remoteView) {
        localRenderer = localView;
        remoteRenderer = remoteView;
        eglBase = EglBase.create();

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions());
        factory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .createPeerConnectionFactory();

        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setMirror(true);
        localRenderer.setEnableHardwareScaler(true);
        localRenderer.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setEnableHardwareScaler(true);
        remoteRenderer.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        List<PeerConnection.IceServer> iceServers = Arrays.asList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        );
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServers);
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        configuration.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) {
                Log.d(TAG, "Signaling: " + state);
            }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                Log.d(TAG, "ICE: " + state);
                if (connectionListener != null) connectionListener.onState(state.toString());
            }
            @Override public void onIceConnectionReceivingChange(boolean receiving) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
            @Override public void onIceCandidate(org.webrtc.IceCandidate candidate) {
                if (iceCandidateListener != null) iceCandidateListener.onCandidate(candidate);
            }
            @Override public void onIceCandidatesRemoved(org.webrtc.IceCandidate[] candidates) { }
            @Override public void onAddStream(org.webrtc.MediaStream stream) { }
            @Override public void onRemoveStream(org.webrtc.MediaStream stream) { }
            @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onAddTrack(RtpReceiver receiver, org.webrtc.MediaStream[] streams) {
                if (receiver.track() instanceof AudioTrack) {
                    ((AudioTrack) receiver.track()).setEnabled(true);
                } else if (receiver.track() instanceof VideoTrack && !remoteVideoAttached) {
                    remoteVideoAttached = true;
                    ((VideoTrack) receiver.track()).addSink(remoteRenderer);
                }
            }
        });

        if (peerConnection == null) throw new IllegalStateException("Could not create WebRTC peer connection");

        audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("privatecall-audio", audioSource);
        localAudioTrack.setEnabled(true);
        peerConnection.addTrack(localAudioTrack, Arrays.asList("privatecall-stream"));

        CameraEnumerator enumerator = Camera2Enumerator.isSupported(context)
                ? new Camera2Enumerator(context) : new Camera1Enumerator(true);
        String cameraName = null;
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) { cameraName = name; break; }
        }
        if (cameraName == null) {
            for (String name : enumerator.getDeviceNames()) {
                if (enumerator.isBackFacing(name)) { cameraName = name; break; }
            }
        }
        if (cameraName == null) throw new IllegalStateException("No usable camera found");
        cameraCapturer = enumerator.createCapturer(cameraName, null);
        if (cameraCapturer == null) throw new IllegalStateException("Could not open camera");

        surfaceTextureHelper = SurfaceTextureHelper.create("PrivateCallCamera", eglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(false);
        cameraCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        cameraCapturer.startCapture(1280, 720, 30);
        localVideoTrack = factory.createVideoTrack("privatecall-video", videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(localRenderer);
        peerConnection.addTrack(localVideoTrack, Arrays.asList("privatecall-stream"));
    }

    public void addIceCandidate(org.webrtc.IceCandidate candidate) {
        if (peerConnection != null) peerConnection.addIceCandidate(candidate);
    }

    public void setMicrophoneEnabled(boolean enabled) {
        if (localAudioTrack != null) localAudioTrack.setEnabled(enabled);
    }

    public void setCameraEnabled(boolean enabled) {
        if (localVideoTrack != null) localVideoTrack.setEnabled(enabled);
    }

    public void switchCamera() {
        if (cameraCapturer != null) cameraCapturer.switchCamera(null);
    }

    public void createOffer(org.webrtc.SdpObserver observer, MediaConstraints constraints) {
        if (peerConnection != null) peerConnection.createOffer(observer, constraints);
    }

    public void createAnswer(org.webrtc.SdpObserver observer, MediaConstraints constraints) {
        if (peerConnection != null) peerConnection.createAnswer(observer, constraints);
    }

    public void setLocalDescription(org.webrtc.SdpObserver observer, SessionDescription description) {
        if (peerConnection != null) peerConnection.setLocalDescription(observer, description);
    }

    public void setRemoteDescription(org.webrtc.SdpObserver observer, SessionDescription description) {
        if (peerConnection != null) peerConnection.setRemoteDescription(observer, description);
    }

    public void close() {
        if (cameraCapturer != null) {
            try { cameraCapturer.stopCapture(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            cameraCapturer.dispose();
            cameraCapturer = null;
        }
        if (localVideoTrack != null) { localVideoTrack.removeSink(localRenderer); localVideoTrack.dispose(); localVideoTrack = null; }
        if (videoSource != null) { videoSource.dispose(); videoSource = null; }
        if (surfaceTextureHelper != null) { surfaceTextureHelper.dispose(); surfaceTextureHelper = null; }
        if (localAudioTrack != null) { localAudioTrack.dispose(); localAudioTrack = null; }
        if (audioSource != null) { audioSource.dispose(); audioSource = null; }
        if (peerConnection != null) { peerConnection.close(); peerConnection.dispose(); peerConnection = null; }
        if (localRenderer != null) { localRenderer.release(); localRenderer = null; }
        if (remoteRenderer != null) { remoteRenderer.release(); remoteRenderer = null; }
        if (factory != null) { factory.dispose(); factory = null; }
        if (eglBase != null) { eglBase.release(); eglBase = null; }
    }
}
