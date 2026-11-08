package com.myapp;
import android.util.Log;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;
import org.json.JSONArray;
import org.webrtc.IceCandidate;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class SocketManager {
 private static SocketManager instance;
 private Socket socket; private Listener listener; private CallListener callListener; private String roomId, peerSocketId="";
 private final List<IceCandidate> queuedCandidates=new ArrayList<>();
 public interface Listener {
  void onStatus(String s); void onMessage(String m,String sender); void onError(String e);
  default void onPeerAvailable(String id){} default void onIncomingOffer(String from,String sdp){} default void onCallEnded(String from){}
 }
 public interface CallListener { void onAnswer(String sdp); void onIceCandidate(IceCandidate c); void onCallEnded(); void onCallRejected(); }
 public static synchronized SocketManager getInstance(){if(instance==null)instance=new SocketManager();return instance;}
 public void setCallListener(CallListener l){callListener=l;if(l!=null&&!queuedCandidates.isEmpty()){for(IceCandidate c:queuedCandidates)l.onIceCandidate(c);queuedCandidates.clear();}}
 public void connect(String url,String room,Listener l)throws URISyntaxException{
  listener=l;roomId=room;
  if(socket!=null&&socket.connected()){joinRoom();return;}
  if(socket!=null){socket.off();socket.disconnect();}
  IO.Options o=new IO.Options();o.reconnection=true;o.reconnectionAttempts=Integer.MAX_VALUE;o.reconnectionDelay=1000;o.timeout=20000;socket=IO.socket(url,o);
  socket.on(Socket.EVENT_CONNECT,a->{status("Connected — joining room…");joinRoom();});
  socket.on("room:joined",a->{JSONObject d=obj(a);JSONArray peers=d==null?null:d.optJSONArray("peers");if(peers!=null&&peers.length()>0){peerSocketId=peers.optString(0,"");if(!peerSocketId.isEmpty()&&listener!=null)listener.onPeerAvailable(peerSocketId);}status("Online — room joined");});
  socket.on("room:user-joined",a->{JSONObject d=obj(a);if(d!=null){peerSocketId=d.optString("socketId","");if(!peerSocketId.isEmpty()&&listener!=null)listener.onPeerAvailable(peerSocketId);}});
  socket.on("chat:message",a->{JSONObject d=obj(a);if(d!=null&&listener!=null){String m=d.optString("message","");if(!m.isEmpty())listener.onMessage(m,d.optString("senderSocketId",""));}});
  socket.on("call:offer",a->{JSONObject d=obj(a);if(d!=null&&listener!=null)listener.onIncomingOffer(d.optString("fromSocketId",""),d.optString("sdp",""));});
  socket.on("call:answer",a->{JSONObject d=obj(a);if(d!=null&&callListener!=null)callListener.onAnswer(d.optString("sdp",""));});
  socket.on("call:ice-candidate",a->{JSONObject d=obj(a);if(d!=null){IceCandidate c=new IceCandidate(d.optString("sdpMid","audio"),d.optInt("sdpMLineIndex",0),d.optString("candidate",""));if(callListener!=null)callListener.onIceCandidate(c);else queuedCandidates.add(c);}});
  socket.on("call:end",a->{JSONObject d=obj(a);if(callListener!=null)callListener.onCallEnded();if(d!=null&&listener!=null)listener.onCallEnded(d.optString("fromSocketId",""));});
  socket.on("call:reject",a->{if(callListener!=null)callListener.onCallRejected();});
  socket.on("server:error",a->{JSONObject d=obj(a);error(d==null?"Server error":d.optString("message","Server error"));});
  socket.on(Socket.EVENT_CONNECT_ERROR,a->{status("Connection error — retrying");error(a.length>0?String.valueOf(a[0]):"Connection error");});
  socket.on(Socket.EVENT_DISCONNECT,a->status("Disconnected — reconnecting…"));
  status("Connecting to server…");socket.connect();
 }
 private JSONObject obj(Object[] a){return a.length>0&&a[0] instanceof JSONObject?(JSONObject)a[0]:null;}
 private void joinRoom(){if(socket==null||!socket.connected()||roomId==null)return;JSONObject d=new JSONObject();try{d.put("roomId",roomId);socket.emit("room:join",d);}catch(Exception e){error("Could not join room");}}
 public boolean isConnected(){return socket!=null&&socket.connected();}
 public String getSocketId(){return socket==null?"":socket.id();}
 public String getPeerSocketId(){return peerSocketId;}
 public void sendChatMessage(String m){JSONObject d=new JSONObject();try{d.put("roomId",roomId);d.put("message",m);socket.emit("chat:message",d);}catch(Exception e){error("Could not send message");}}
 public void sendOffer(String to,String sdp){signal("call:offer",to,sdp);}
 public void sendAnswer(String to,String sdp){signal("call:answer",to,sdp);}
 private void signal(String event,String to,String sdp){JSONObject d=new JSONObject();try{d.put("targetSocketId",to);d.put("sdp",sdp);socket.emit(event,d);}catch(Exception e){error("Could not send call signal");}}
 public void sendIceCandidate(String to,IceCandidate c){JSONObject d=new JSONObject();try{d.put("targetSocketId",to);d.put("candidate",c.sdp);d.put("sdpMid",c.sdpMid);d.put("sdpMLineIndex",c.sdpMLineIndex);socket.emit("call:ice-candidate",d);}catch(Exception e){error("Could not send audio candidate");}}
 public void sendCallEnd(String to){simpleSignal("call:end",to);} public void sendCallReject(String to){simpleSignal("call:reject",to);}
 private void simpleSignal(String event,String to){JSONObject d=new JSONObject();try{d.put("targetSocketId",to);socket.emit(event,d);}catch(Exception e){error("Could not send call signal");}}
 private void status(String s){if(listener!=null)listener.onStatus(s);} private void error(String s){if(listener!=null)listener.onError(s);}
}
