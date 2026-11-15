const http = require("node:http");
const path = require("node:path");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const { Server } = require("socket.io");

const app = express();
const server = http.createServer(app);

const PORT = Number(process.env.PORT || 3000);
const CLIENT_ORIGIN = process.env.CLIENT_ORIGIN || "*";
const MAX_MESSAGE_LENGTH = 4000;
const MAX_ROOM_LENGTH = 80;

app.disable("x-powered-by");
app.use(helmet());
app.use(cors({ origin: CLIENT_ORIGIN === "*" ? "*" : CLIENT_ORIGIN }));
app.use(express.json({ limit: "16kb" }));
app.use("/test-client", express.static(path.join(__dirname, "public")));

app.get("/", (_req, res) => {
  res.status(200).json({
    name: "PrivateCall Backend",
    status: "online",
    socketPath: "/socket.io"
  });
});

app.get("/health", (_req, res) => {
  res.status(200).json({ ok: true, uptime: Math.floor(process.uptime()) });
});

const io = new Server(server, {
  cors: {
    origin: CLIENT_ORIGIN,
    methods: ["GET", "POST"]
  },
  maxHttpBufferSize: 1e6,
  connectTimeout: 20000
});

function cleanString(value, maxLength) {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, maxLength);
}

function validRoomId(value) {
  const roomId = cleanString(value, MAX_ROOM_LENGTH);
  return /^[a-zA-Z0-9_-]{1,80}$/.test(roomId) ? roomId : "";
}

function emitError(socket, code, message) {
  socket.emit("server:error", { code, message });
}

io.on("connection", (socket) => {
  console.log("Socket connected:", socket.id);

  socket.on("room:join", (payload = {}) => {
    const roomId = validRoomId(payload.roomId);
    if (!roomId) {
      emitError(socket, "INVALID_ROOM", "A valid roomId is required.");
      return;
    }

    socket.join(roomId);
    const peers = Array.from(io.sockets.adapter.rooms.get(roomId) || []).filter(id => id !== socket.id);
    socket.emit("room:joined", { roomId, socketId: socket.id, peers });
    socket.to(roomId).emit("room:user-joined", { socketId: socket.id });
  });

  socket.on("chat:message", (payload = {}) => {
    const roomId = validRoomId(payload.roomId);
    const message = cleanString(payload.message, MAX_MESSAGE_LENGTH);

    if (!roomId || !message) {
      emitError(socket, "INVALID_MESSAGE", "A valid roomId and message are required.");
      return;
    }
    if (!socket.rooms.has(roomId)) {
      emitError(socket, "NOT_IN_ROOM", "Join the room before sending a message.");
      return;
    }

    const messageData = {
      roomId,
      message,
      senderSocketId: socket.id,
      sentAt: new Date().toISOString()
    };

    io.to(roomId).emit("chat:message", messageData);
  });

  // These events relay WebRTC signaling only. Media is exchanged peer-to-peer.
  for (const eventName of ["call:offer", "call:answer", "call:ice-candidate", "call:reject", "call:end"]) {
    socket.on(eventName, (payload = {}) => {
      const targetSocketId = cleanString(payload.targetSocketId, 100);
      if (!targetSocketId || targetSocketId === socket.id) {
        emitError(socket, "INVALID_TARGET", "A valid targetSocketId is required.");
        return;
      }

      const { targetSocketId: _ignored, ...data } = payload;
      io.to(targetSocketId).emit(eventName, {
        ...data,
        fromSocketId: socket.id,
        sentAt: new Date().toISOString()
      });
    });
  }

  socket.on("disconnect", (reason) => {
    console.log("Socket disconnected:", socket.id, reason);
  });
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`PrivateCall backend listening on port ${PORT}`);
});
