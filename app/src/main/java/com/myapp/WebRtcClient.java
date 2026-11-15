package com.myapp;

import android.content.Context;
import android.util.Log;
import org.webrtc.*;
import java.util.Arrays;
import java.util.List;

public class WebRtcClient {
    private static final String TAG="PrivateCallWebRTC";
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private CameraVideoCapturer cameraCapturer;
    private SurfaceTextureHelper textureHelper;
    private EglBase eglBase;
    private SurfaceViewRenderer localRenderer, remoteRenderer;
    private IceCandidateListener iceCandidateListener;
    private boolean cameraEnabled=true;
    public interface IceCandidateListener { void onCandidate(IceCandidate candidate); }
    public void setIceCandidateListener(IceCandidateListener l){iceCandidateListener=l;}

    public void initialize(Context context, SurfaceViewRenderer local, SurfaceViewRenderer remote) {
        eglBase=EglBase.create();
        localRenderer=local; remoteRenderer=remote;
        localRenderer.init(eglBase.getEglBaseContext(),null);
        localRenderer.setMirror(true);
        localRenderer.setEnableHardwareScaler(true);
        remoteRenderer.init(eglBase.getEglBaseContext(),null);
        remoteRenderer.setMirror(false);
        remoteRenderer.setEnableHardwareScaler(true);

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions());
        factory=PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(),true,true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .setOptions(new PeerConnectionFactory.Options()).createPeerConnectionFactory();

        List<PeerConnection.IceServer> servers=Arrays.asList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        PeerConnection.RTCConfiguration config=new PeerConnection.RTCConfiguration(servers);
        config.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peerConnection=factory.createPeerConnection(config,new PeerConnection.Observer(){
            @Override public void onSignalingChange(PeerConnection.SignalingState s){Log.d(TAG,"Signaling "+s);}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s){
                Log.d(TAG,"ICE "+s);
                if(videoSource!=null){
                    if(s==PeerConnection.IceConnectionState.DISCONNECTED || s==PeerConnection.IceConnectionState.FAILED) videoSource.adaptOutputFormat(320,240,10);
                    else if(s==PeerConnection.IceConnectionState.CONNECTED || s==PeerConnection.IceConnectionState.COMPLETED) videoSource.adaptOutputFormat(640,480,20);
                }
            }
            @Override public void onIceConnectionReceivingChange(boolean b){}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s){}
            @Override public void onIceCandidate(IceCandidate c){if(iceCandidateListener!=null)iceCandidateListener.onCandidate(c);}
            @Override public void onIceCandidatesRemoved(IceCandidate[] c){}
            @Override public void onAddStream(MediaStream s){}
            @Override public void onRemoveStream(MediaStream s){}
            @Override public void onDataChannel(DataChannel c){}
            @Override public void onRenegotiationNeeded(){}
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams){
                MediaStreamTrack track=receiver.track();
                if(track instanceof AudioTrack)((AudioTrack)track).setEnabled(true);
                if(track instanceof VideoTrack){
                    VideoTrack remoteTrack=(VideoTrack)track;
                    remoteTrack.setEnabled(true);
                    remoteRenderer.post(()->remoteTrack.addSink(remoteRenderer));
                }
            }
        });
        if(peerConnection==null)throw new IllegalStateException("Could not create peer connection");

        audioSource=factory.createAudioSource(new MediaConstraints());
        localAudioTrack=factory.createAudioTrack("privatecall-microphone",audioSource);
        peerConnection.addTrack(localAudioTrack,Arrays.asList("privatecall-stream"));

        videoSource=factory.createVideoSource(false);
        textureHelper=SurfaceTextureHelper.create("PrivateCallCamera",eglBase.getEglBaseContext());
        Camera2Enumerator enumerator=new Camera2Enumerator(context);
        String cameraName=null;
        for(String name:enumerator.getDeviceNames())if(enumerator.isFrontFacing(name)){cameraName=name;break;}
        if(cameraName==null){for(String name:enumerator.getDeviceNames())if(enumerator.isBackFacing(name)){cameraName=name;break;}}
        if(cameraName==null)throw new IllegalStateException("No usable camera found");
        cameraCapturer=enumerator.createCapturer(cameraName,null);
        if(cameraCapturer==null)throw new IllegalStateException("Could not open camera");
        cameraCapturer.initialize(textureHelper,context,videoSource.getCapturerObserver());
        cameraCapturer.startCapture(640,480,20);
        localVideoTrack=factory.createVideoTrack("privatecall-camera",videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(localRenderer);
        peerConnection.addTrack(localVideoTrack,Arrays.asList("privatecall-stream"));
    }
    public void setMicrophoneEnabled(boolean enabled){if(localAudioTrack!=null)localAudioTrack.setEnabled(enabled);}
    public void setCameraEnabled(boolean enabled){cameraEnabled=enabled;if(localVideoTrack!=null)localVideoTrack.setEnabled(enabled);}
    public boolean isCameraEnabled(){return cameraEnabled;}
    public void switchCamera(){if(cameraCapturer!=null)cameraCapturer.switchCamera(null);}
    public void addIceCandidate(IceCandidate c){if(peerConnection!=null)peerConnection.addIceCandidate(c);}
    public void createOffer(SdpObserver o){if(peerConnection!=null)peerConnection.createOffer(o,new MediaConstraints());}
    public void createOffer(SdpObserver o,MediaConstraints c){if(peerConnection!=null)peerConnection.createOffer(o,c);}
    public void createAnswer(SdpObserver o,MediaConstraints c){if(peerConnection!=null)peerConnection.createAnswer(o,c);}
    public void setLocalDescription(SdpObserver o,SessionDescription d){if(peerConnection!=null)peerConnection.setLocalDescription(o,d);}
    public void setRemoteDescription(SdpObserver o,SessionDescription d){if(peerConnection!=null)peerConnection.setRemoteDescription(o,d);}
    public void close(){
        if(cameraCapturer!=null){try{cameraCapturer.stopCapture();}catch(Exception ignored){}cameraCapturer.dispose();cameraCapturer=null;}
        if(localVideoTrack!=null){localVideoTrack.removeSink(localRenderer);localVideoTrack.dispose();localVideoTrack=null;}
        if(videoSource!=null){videoSource.dispose();videoSource=null;}
        if(textureHelper!=null){textureHelper.dispose();textureHelper=null;}
        if(localAudioTrack!=null){localAudioTrack.dispose();localAudioTrack=null;}
        if(audioSource!=null){audioSource.dispose();audioSource=null;}
        if(peerConnection!=null){peerConnection.close();peerConnection.dispose();peerConnection=null;}
        if(factory!=null){factory.dispose();factory=null;}
        if(localRenderer!=null){localRenderer.release();localRenderer=null;}
        if(remoteRenderer!=null){remoteRenderer.release();remoteRenderer=null;}
        if(eglBase!=null){eglBase.release();eglBase=null;}
    }
}