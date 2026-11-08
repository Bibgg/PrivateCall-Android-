package com.myapp;
import android.app.Activity;import android.os.Bundle;import android.widget.Button;import android.widget.EditText;import android.widget.TextView;import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;import androidx.recyclerview.widget.RecyclerView;
import java.net.URISyntaxException;import java.util.ArrayList;import java.util.List;
public class ChatActivity extends Activity {
 private static final String SERVER_URL="https://privatecall-server.onrender.com",ROOM_ID="privatecall-test-room";
 private final List<String> messages=new ArrayList<>();private ChatAdapter adapter;private SocketManager socketManager;private TextView connectionStatus;private RecyclerView recycler;
 @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_chat);
  recycler=findViewById(R.id.recyclerMessages);EditText input=findViewById(R.id.editMessage);Button send=findViewById(R.id.buttonSend),call=findViewById(R.id.buttonCall);connectionStatus=findViewById(R.id.connectionStatus);
  adapter=new ChatAdapter(messages);recycler.setLayoutManager(new LinearLayoutManager(this));recycler.setAdapter(adapter);messages.add("Welcome to PrivateCall. Connecting…");adapter.notifyItemInserted(0);
  socketManager=SocketManager.getInstance();connectServer();
  send.setOnClickListener(v->{String m=input.getText().toString().trim();if(m.isEmpty())return;if(!socketManager.isConnected()){Toast.makeText(this,"Server is not connected",Toast.LENGTH_SHORT).show();return;}socketManager.sendChatMessage(m);input.setText("");});
  call.setOnClickListener(v->{String peer=socketManager.getPeerSocketId();if(peer.isEmpty()){Toast.makeText(this,"पहले दूसरा मोबाइल इसी chat room में जोड़ें",Toast.LENGTH_LONG).show();return;}android.content.Intent i=new android.content.Intent(this,CallActivity.class);i.putExtra("targetSocketId",peer);startActivity(i);});
 }
 private void connectServer(){try{socketManager.connect(SERVER_URL,ROOM_ID,new SocketManager.Listener(){
  public void onStatus(String s){runOnUiThread(()->connectionStatus.setText(s));}
  public void onMessage(String m,String sender){runOnUiThread(()->{messages.add((sender.equals(socketManager.getSocketId())?"You: ":"Other: ")+m);adapter.notifyItemInserted(messages.size()-1);recycler.scrollToPosition(messages.size()-1);});}
  public void onError(String e){runOnUiThread(()->Toast.makeText(ChatActivity.this,e,Toast.LENGTH_SHORT).show());}
  public void onPeerAvailable(String id){runOnUiThread(()->connectionStatus.setText("Online — another user is ready"));}
  public void onIncomingOffer(String from,String sdp){runOnUiThread(()->{android.content.Intent i=new android.content.Intent(ChatActivity.this,CallActivity.class);i.putExtra("incoming",true);i.putExtra("targetSocketId",from);i.putExtra("remoteSdp",sdp);startActivity(i);});}
  public void onCallEnded(String from){runOnUiThread(()->Toast.makeText(ChatActivity.this,"दूसरे व्यक्ति ने कॉल बंद कर दी",Toast.LENGTH_SHORT).show());}
 });}catch(URISyntaxException e){connectionStatus.setText("Invalid server URL");}}
}
