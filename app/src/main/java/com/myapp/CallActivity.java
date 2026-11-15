package com.myapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.webrtc.*;
import java.util.ArrayList;
import java.util.List;

public class CallActivity extends Activity implements SocketManager.CallListener {
    private static final int PERMISSION_REQUEST=41;
    private WebRtcClient rtc; private SocketManager socket;
    private TextView status; private Button mic,accept,end,camera,switchCamera;
    private SurfaceViewRenderer localVideo,remoteVideo;
    private boolean incoming,accepted,ending,micOn=true,remoteDescriptionSet=false;
    private String target="",offer="";
    private final List<IceCandidate> pending=new ArrayList<>();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);setContentView(R.layout.activity_call);
        status=findViewById(R.id.callStatus);mic=findViewById(R.id.buttonMicrophone);
        accept=findViewById(R.id.buttonAcceptCall);end=findViewById(R.id.buttonEndCall);
        camera=findViewById(R.id.buttonCamera);switchCamera=findViewById(R.id.buttonSwitchCamera);
        localVideo=findViewById(R.id.localVideo);remoteVideo=findViewById(R.id.remoteVideo);
        socket=SocketManager.getInstance();socket.setCallListener(this);
        incoming=getIntent().getBooleanExtra("incoming",false);
        target=getIntent().getStringExtra("targetSocketId");offer=getIntent().getStringExtra("remoteSdp");
        if(target==null)target="";if(offer==null)offer="";
        accept.setVisibility(incoming?View.VISIBLE:View.GONE);accept.setEnabled(false);
        end.setText(incoming?"Reject call":"End call");
        mic.setOnClickListener(v->toggleMic());camera.setOnClickListener(v->toggleCamera());
        switchCamera.setOnClickListener(v->{if(rtc!=null)rtc.switchCamera();});
        accept.setOnClickListener(v->acceptCall());end.setOnClickListener(v->endCall());
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED ||
           checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA},PERMISSION_REQUEST);
        }else initVideoCall();
    }
    private void initVideoCall(){
        try{
            rtc=new WebRtcClient();rtc.setIceCandidateListener(c->{if(!target.isEmpty())socket.sendIceCandidate(target,c);});
            rtc.initialize(this,localVideo,remoteVideo);
            mic.setEnabled(true);camera.setEnabled(true);switchCamera.setEnabled(true);
            if(incoming){status.setText("Incoming video call — Accept दबाएँ");accept.setEnabled(!offer.isEmpty());}
            else{status.setText("Calling… video और audio भेजे जा रहे हैं");makeOffer();}
        }catch(Exception e){status.setText("Camera/audio शुरू नहीं हुआ");Toast.makeText(this,String.valueOf(e.getMessage()),Toast.LENGTH_LONG).show();}
    }
    private SdpObserver observer(java.util.function.Consumer<SessionDescription> success,String label){
        return new SdpObserver(){
            public void onCreateSuccess(SessionDescription s){success.accept(s);}
            public void onSetSuccess(){success.accept(null);}
            public void onCreateFailure(String e){show(label+e);restoreAccept();}
            public void onSetFailure(String e){show(label+e);restoreAccept();}
        };
    }
    private void makeOffer(){
        rtc.createOffer(observer(s->rtc.setLocalDescription(observer(x->socket.sendOffer(target,s.description),"Offer setup failed: "),s),"Offer failed: "));
    }
    private void restoreAccept(){if(incoming&&!accepted)runOnUiThread(()->accept.setEnabled(rtc!=null&&!offer.isEmpty()));}
    private void acceptCall(){
        if(rtc==null){show("Camera/microphone तैयार हो रहे हैं");return;}
        if(offer.isEmpty()){show("Call offer नहीं मिला — दोबारा कॉल करें");return;}
        accept.setEnabled(false);status.setText("Video call connect हो रही है…");
        SessionDescription remote=new SessionDescription(SessionDescription.Type.OFFER,offer);
        rtc.setRemoteDescription(observer(s->{remoteDescriptionSet=true;drain();rtc.createAnswer(observer(answer->rtc.setLocalDescription(observer(x->{socket.sendAnswer(target,answer.description);accepted=true;show("Video call connected");},"Answer setup failed: "),answer),"Answer failed: "),new MediaConstraints());},"Incoming setup failed: "),remote);
    }
    @Override public void onAnswer(String sdp){
        SessionDescription a=new SessionDescription(SessionDescription.Type.ANSWER,sdp);
        rtc.setRemoteDescription(observer(s->{remoteDescriptionSet=true;accepted=true;show("Video call connected");drain();},"Answer error: "),a);
    }
    @Override public void onIceCandidate(IceCandidate c){if(rtc==null||!remoteDescriptionSet)pending.add(c);else rtc.addIceCandidate(c);}
    private void drain(){for(IceCandidate c:pending)rtc.addIceCandidate(c);pending.clear();}
    @Override public void onCallEnded(){show("Call ended");runOnUiThread(this::finish);}
    @Override public void onCallRejected(){show("Call rejected");runOnUiThread(this::finish);}
    private void toggleMic(){micOn=!micOn;if(rtc!=null)rtc.setMicrophoneEnabled(micOn);mic.setText(micOn?"Mute microphone":"Unmute microphone");}
    private void toggleCamera(){if(rtc==null)return;boolean on=!rtc.isCameraEnabled();rtc.setCameraEnabled(on);camera.setText(on?"Camera OFF":"Camera ON");status.setText(on?"Camera चालू":"Camera बंद");}
    private void endCall(){if(ending)return;ending=true;if(!target.isEmpty()){if(incoming&&!accepted)socket.sendCallReject(target);else socket.sendCallEnd(target);}finish();}
    private void show(String s){runOnUiThread(()->status.setText(s));}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==PERMISSION_REQUEST&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED)initVideoCall();
        else{status.setText("वीडियो कॉल के लिए Camera और Microphone permission जरूरी है");mic.setEnabled(false);camera.setEnabled(false);accept.setEnabled(false);}
    }
    @Override protected void onDestroy(){socket.setCallListener(null);if(rtc!=null)rtc.close();super.onDestroy();}
}