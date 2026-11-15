package com.myapp;
import android.Manifest;import android.app.Activity;import android.content.pm.PackageManager;import android.os.Bundle;import android.view.View;import android.widget.Button;import android.widget.TextView;import android.widget.Toast;
import org.webrtc.IceCandidate;import org.webrtc.SdpObserver;import org.webrtc.SessionDescription;import java.util.ArrayList;import java.util.List;
public class CallActivity extends Activity implements SocketManager.CallListener {
 private static final int PERMISSION_REQUEST=41;private WebRtcClient rtc;private SocketManager socket;private TextView status;private Button mic,accept,end;
 private boolean incoming,accepted,ending,micOn=true,remoteDescriptionSet=false;private String target="",offer="";private final List<IceCandidate> pending=new ArrayList<>();
 @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_call);status=findViewById(R.id.callStatus);mic=findViewById(R.id.buttonMicrophone);accept=findViewById(R.id.buttonAcceptCall);end=findViewById(R.id.buttonEndCall);
  socket=SocketManager.getInstance();socket.setCallListener(this);incoming=getIntent().getBooleanExtra("incoming",false);target=getIntent().getStringExtra("targetSocketId");offer=getIntent().getStringExtra("remoteSdp");if(target==null)target="";if(offer==null)offer="";
  accept.setVisibility(incoming?View.VISIBLE:View.GONE);accept.setEnabled(false);end.setText(incoming?"Reject call":"End call");mic.setOnClickListener(v->toggleMic());accept.setOnClickListener(v->acceptCall());end.setOnClickListener(v->endCall());
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION_REQUEST);else initAudio();
 }
 private void initAudio(){try{rtc=new WebRtcClient();rtc.setIceCandidateListener(c->{if(!target.isEmpty())socket.sendIceCandidate(target,c);});rtc.initialize(this);mic.setEnabled(true);if(incoming){status.setText("Incoming audio call — tap Accept");accept.setEnabled(!offer.isEmpty());}else{status.setText("Calling… waiting for answer");makeOffer();}}catch(Exception e){status.setText("Audio initialization failed");Toast.makeText(this,String.valueOf(e.getMessage()),Toast.LENGTH_LONG).show();}}
 private SdpObserver observer(java.util.function.Consumer<SessionDescription> success,String failureLabel){return new SdpObserver(){public void onCreateSuccess(SessionDescription s){success.accept(s);}public void onSetSuccess(){success.accept(null);}public void onCreateFailure(String e){show(failureLabel+e);restoreAcceptAfterFailure();}public void onSetFailure(String e){show(failureLabel+e);restoreAcceptAfterFailure();}};}
 private void makeOffer(){rtc.createOffer(observer(s->rtc.setLocalDescription(observer(x->socket.sendOffer(target,s.description),"Offer setup failed: "),s),"Offer failed: "),new org.webrtc.MediaConstraints());}
 private void restoreAcceptAfterFailure(){if(incoming&&!accepted)runOnUiThread(()->accept.setEnabled(rtc!=null&&!offer.isEmpty()));}
 private void acceptCall(){if(rtc==null){show("Microphone is still preparing — please wait");return;}if(offer.isEmpty()){show("Call offer is missing — ask the caller to call again");return;}accept.setEnabled(false);status.setText("Accepting call… setting up audio");SessionDescription remote=new SessionDescription(SessionDescription.Type.OFFER,offer);rtc.setRemoteDescription(observer(s->{remoteDescriptionSet=true;drain();rtc.createAnswer(observer(answer->rtc.setLocalDescription(observer(x->{socket.sendAnswer(target,answer.description);accepted=true;show("Connected — audio call active");},"Answer setup failed: "),answer),"Answer failed: "),new org.webrtc.MediaConstraints());},"Incoming setup failed: "),remote);}
 @Override public void onAnswer(String sdp){SessionDescription a=new SessionDescription(SessionDescription.Type.ANSWER,sdp);rtc.setRemoteDescription(observer(s->{remoteDescriptionSet=true;accepted=true;show("Connected — audio call active");drain();},"Answer error: "),a);}
 @Override public void onIceCandidate(IceCandidate c){if(rtc==null||!remoteDescriptionSet)pending.add(c);else rtc.addIceCandidate(c);}
 private void drain(){for(IceCandidate c:pending)rtc.addIceCandidate(c);pending.clear();}
 @Override public void onCallEnded(){show("Call ended");runOnUiThread(this::finish);}
 @Override public void onCallRejected(){show("Call rejected");runOnUiThread(this::finish);}
 private void toggleMic(){micOn=!micOn;if(rtc!=null)rtc.setMicrophoneEnabled(micOn);mic.setText(micOn?"Mute microphone":"Unmute microphone");status.setText(micOn?"Microphone is on":"Microphone is muted");}
 private void endCall(){if(ending)return;ending=true;if(!target.isEmpty()){if(incoming&&!accepted)socket.sendCallReject(target);else socket.sendCallEnd(target);}finish();}
 private void show(String s){runOnUiThread(()->status.setText(s));}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==PERMISSION_REQUEST&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)initAudio();else{status.setText("Microphone permission required");mic.setEnabled(false);accept.setEnabled(false);}}
 @Override protected void onDestroy(){socket.setCallListener(null);if(rtc!=null)rtc.close();super.onDestroy();}
}
