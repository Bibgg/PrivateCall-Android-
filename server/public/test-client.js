(() => {
 const $=id=>document.getElementById(id);
 const status=s=>$("status").textContent=s;
 const callStatus=s=>$("callStatus").textContent=s;
 let socket=null, roomId="", peers=[], selected="", pc=null, localStream=null, remoteSet=false, pendingIce=[], currentPeer="";
 const config={iceServers:[{urls:"stun:stun.l.google.com:19302"}]};
 function setConnected(on){$("join").disabled=on;$("refresh").disabled=!on;$("call").disabled=!on||!selected;$("hangup").disabled=!pc;}
 function renderPeers(){
   const list=$("peers");list.innerHTML="";
   if(!peers.length){list.innerHTML='<li class="small">No other peers in this room yet.</li>';selected="";$("call").disabled=true;return;}
   peers.forEach(id=>{const li=document.createElement("li");const b=document.createElement("button");b.className="secondary";b.textContent=(id===selected?"✓ ":"")+id;b.onclick=()=>{selected=id;renderPeers();};li.appendChild(b);list.appendChild(li);});
   $("call").disabled=!socket||!socket.connected||!selected||!!pc;
 }
 function addPeer(id){if(!id||id===socket.id)return;if(!peers.includes(id))peers.push(id);renderPeers();}
 function removePeer(id){peers=peers.filter(x=>x!==id);if(selected===id)selected="";renderPeers();}
 function send(event,data){if(!socket||!socket.connected||!currentPeer)return;socket.emit(event,{targetSocketId:currentPeer,...data});}
 async function getPC(){
   if(pc)return pc;
   localStream=await navigator.mediaDevices.getUserMedia({audio:true,video:{width:{ideal:640},height:{ideal:480},frameRate:{ideal:20,max:24}}});
   pc=new RTCPeerConnection(config);$("localVideo").srcObject=localStream;$("localVideo").play().catch(()=>{});
   localStream.getTracks().forEach(t=>pc.addTrack(t,localStream));
   pc.ontrack=e=>{const stream=e.streams[0];if(e.track.kind==="video"){$("remoteVideo").srcObject=stream;$("remoteVideo").play().catch(()=>{});}if(e.track.kind==="audio"){$("remoteAudio").srcObject=stream;$("remoteAudio").play().catch(()=>{});}};
   pc.onicecandidate=e=>{if(e.candidate)send("call:ice-candidate",{candidate:e.candidate.candidate,sdpMid:e.candidate.sdpMid,sdpMLineIndex:e.candidate.sdpMLineIndex});};
   pc.onconnectionstatechange=()=>{callStatus("WebRTC: "+pc.connectionState);if(pc.connectionState==="failed"||pc.connectionState==="closed")cleanup(false);};
   pc.oniceconnectionstatechange=()=>{if(pc)callStatus("ICE: "+pc.iceConnectionState);};
   $("hangup").disabled=false;
   return pc;
 }
 async function drainIce(){if(!pc||!remoteSet)return;for(const c of pendingIce.splice(0)){try{await pc.addIceCandidate(c);}catch(e){console.warn("ICE add failed",e);}}}
 async function startCall(){
   if(!selected)return;currentPeer=selected;
   try{const p=await getPC();const offer=await p.createOffer();await p.setLocalDescription(offer);send("call:offer",{sdp:p.localDescription.sdp});callStatus("Offer sent. Waiting for answer…");}
   catch(e){callStatus("Call setup error: "+e.message);cleanup(false);}
 }
 async function receiveOffer(data){
   if(pc)cleanup(false);
   currentPeer=data.fromSocketId;
   try{const p=await getPC();await p.setRemoteDescription({type:"offer",sdp:data.sdp});remoteSet=true;await drainIce();const answer=await p.createAnswer();await p.setLocalDescription(answer);send("call:answer",{sdp:p.localDescription.sdp});callStatus("Answer sent. Connecting…");}
   catch(e){callStatus("Could not answer: "+e.message);cleanup(false);}
 }
 async function receiveAnswer(data){
   if(!pc||data.fromSocketId!==currentPeer)return;
   try{await pc.setRemoteDescription({type:"answer",sdp:data.sdp});remoteSet=true;await drainIce();callStatus("Answer received. Connecting…");}
   catch(e){callStatus("Answer error: "+e.message);}
 }
 async function receiveIce(data){
   if(data.fromSocketId!==currentPeer)return;
   const c={candidate:data.candidate,sdpMid:data.sdpMid,sdpMLineIndex:data.sdpMLineIndex};
   if(!pc||!remoteSet){pendingIce.push(c);return;}
   try{await pc.addIceCandidate(c);}catch(e){console.warn("ICE add failed",e);}
 }
 function cleanup(notify){
   if(notify&&currentPeer)send("call:end",{});
   if(pc){pc.ontrack=null;pc.onicecandidate=null;pc.close();}
   pc=null;remoteSet=false;pendingIce=[];currentPeer="";$("localVideo").srcObject=null;$("remoteVideo").srcObject=null;
   if(localStream){localStream.getTracks().forEach(t=>t.stop());localStream=null;}
   $("remoteAudio").srcObject=null;$("hangup").disabled=true;
   $("call").disabled=!socket||!socket.connected||!selected;
   callStatus("No active call");
 }
 $("join").onclick=()=>{
   roomId=$("room").value.trim();if(!roomId){status("Enter a room ID");return;}
   if(!/^[a-zA-Z0-9_-]{1,80}$/.test(roomId)){status("Room ID can use letters, numbers, _ and - only (max 80)");return;}
   socket=io({transports:["websocket","polling"]});
   socket.on("connect",()=>{status("Connected: "+socket.id+" — joining "+roomId);socket.emit("room:join",{roomId});});
   socket.on("room:joined",d=>{peers=(d.peers||[]);renderPeers();status("Joined room: "+d.roomId);setConnected(true);});
   socket.on("room:user-joined",d=>{addPeer(d.socketId);status("Peer joined: "+d.socketId);});
   socket.on("call:offer",receiveOffer);
   socket.on("call:answer",receiveAnswer);
   socket.on("call:ice-candidate",receiveIce);
   socket.on("call:end",d=>{if(d.fromSocketId===currentPeer){cleanup(false);callStatus("Other peer ended the call");}});
   socket.on("call:reject",d=>{if(d.fromSocketId===currentPeer){cleanup(false);callStatus("Call rejected");}});
   socket.on("server:error",d=>status("Server error: "+(d.message||d.code)));
   socket.on("disconnect",()=>{status("Disconnected");setConnected(false);cleanup(false);});
   socket.on("connect_error",e=>status("Connection error: "+e.message));
 };
 $("refresh").onclick=()=>{if(socket&&socket.connected){socket.emit("room:join",{roomId});status("Refreshing room peers…");}};
 $("call").onclick=startCall;
 $("hangup").onclick=()=>cleanup(true);
})();
