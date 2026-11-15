const http = require("node:http");
const crypto = require("node:crypto");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const { MongoClient, ObjectId } = require("mongodb");
const { Server } = require("socket.io");

const app = express();
const server = http.createServer(app);
const PORT = Number(process.env.PORT || 3000);
const CLIENT_ORIGIN = process.env.CLIENT_ORIGIN || "*";
const JWT_SECRET = process.env.JWT_SECRET;
const MONGODB_URI = process.env.MONGODB_URI;
const DB_NAME = process.env.MONGODB_DATABASE || "privatecall";
const MAX_MESSAGE_LENGTH = 12000;
let db;
let mongoClient;
const c = {};
const io = new Server(server, { cors: { origin: CLIENT_ORIGIN, methods: ["GET", "POST"] }, maxHttpBufferSize: 256 * 1024, connectTimeout: 20000 });

app.disable("x-powered-by");
app.use(helmet());
app.use(cors({ origin: CLIENT_ORIGIN }));
app.use(express.json({ limit: "32kb" }));

class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
const clean = (v, n = 120) => typeof v === "string" ? v.trim().slice(0, n) : "";
const oid = id => { if (!ObjectId.isValid(id)) throw new ApiError(400, "Invalid ID."); return new ObjectId(id); };
const safeUser = u => ({ id: String(u._id), username: u.username, displayName: u.displayName, createdAt: u.createdAt });
const keyFor = ids => crypto.createHash("sha256").update([...ids].sort().join(":")).digest("hex");
function tokenFor(user) {
  if (!JWT_SECRET || JWT_SECRET.length < 32) throw new ApiError(503, "Server authentication is not configured.");
  return jwt.sign({ sub: String(user._id) }, JWT_SECRET, { expiresIn: "30d", issuer: "privatecall" });
}
function verify(token) {
  const p = jwt.verify(token, JWT_SECRET, { issuer: "privatecall" });
  if (!ObjectId.isValid(p.sub)) throw new Error("Invalid token");
  return p.sub;
}
function auth(req, _res, next) {
  try {
    const h = clean(req.headers.authorization, 2048);
    if (!h.startsWith("Bearer ")) throw new ApiError(401, "Please sign in.");
    req.userId = verify(h.slice(7)); next();
  } catch (e) { next(e instanceof ApiError ? e : new ApiError(401, "Invalid or expired session.")); }
}
function dbReady(_req, _res, next) { if (!db) return next(new ApiError(503, "Database is not configured. Add MONGODB_URI in Render.")); next(); }
async function memberConversation(id, userId) {
  const row = await c.conversations.findOne({ _id: oid(id), members: userId });
  if (!row) throw new ApiError(404, "Conversation not found.");
  return row;
}
async function getOrCreateConversation(a, b) {
  if (String(a) === String(b)) throw new ApiError(400, "Cannot chat with yourself.");
  const members = [String(a), String(b)].sort();
  const key = keyFor(members), now = new Date();
  await c.conversations.updateOne({ key }, { $setOnInsert: { key, members, createdAt: now, updatedAt: now, lastMessageAt: null } }, { upsert: true });
  return c.conversations.findOne({ key });
}

app.get("/", (_req, res) => res.json({ name: "PrivateCall Backend", status: db ? "online" : "waiting-for-database" }));
app.get("/health", (_req, res) => res.status(db ? 200 : 503).json({ ok: Boolean(db), database: db ? "connected" : "not_configured", uptime: Math.floor(process.uptime()) }));

app.post("/api/auth/register", dbReady, async (req, res, next) => {
  try {
    const username = clean(req.body.username, 24).toLowerCase();
    const displayName = clean(req.body.displayName, 48);
    const password = typeof req.body.password === "string" ? req.body.password : "";
    if (!/^[a-z0-9_]{3,24}$/.test(username)) throw new ApiError(400, "Username must be 3–24 letters, numbers or underscores.");
    if (!displayName) throw new ApiError(400, "Display name is required.");
    if (password.length < 10 || password.length > 128) throw new ApiError(400, "Password must be 10–128 characters.");
    const user = { username, displayName, passwordHash: await bcrypt.hash(password, 12), createdAt: new Date(), status: "active" };
    try { user._id = (await c.users.insertOne(user)).insertedId; }
    catch (e) { if (e.code === 11000) throw new ApiError(409, "Username is already taken."); throw e; }
    res.status(201).json({ token: tokenFor(user), user: safeUser(user) });
  } catch (e) { next(e); }
});
app.post("/api/auth/login", dbReady, async (req, res, next) => {
  try {
    const username = clean(req.body.username, 24).toLowerCase();
    const password = typeof req.body.password === "string" ? req.body.password : "";
    const user = await c.users.findOne({ username, status: "active" });
    if (!user || !(await bcrypt.compare(password, user.passwordHash))) throw new ApiError(401, "Username or password is incorrect.");
    res.json({ token: tokenFor(user), user: safeUser(user) });
  } catch (e) { next(e); }
});
app.get("/api/me", dbReady, auth, async (req, res, next) => {
  try { const u = await c.users.findOne({ _id: oid(req.userId), status: "active" }); if (!u) throw new ApiError(401, "Account not found."); res.json({ user: safeUser(u) }); }
  catch (e) { next(e); }
});
app.get("/api/users/search", dbReady, auth, async (req, res, next) => {
  try {
    const q = clean(req.query.q, 40).toLowerCase();
    if (q.length < 2) throw new ApiError(400, "Enter at least 2 characters.");
    const escaped = q.replace(/[.*+?^$()|[\]\\]/g, "\\$&");
    const rows = await c.users.find({ status: "active", _id: { $ne: oid(req.userId) }, $or: [
      { username: { $regex: "^" + escaped, $options: "i" } },
      { displayName: { $regex: escaped, $options: "i" } }
    ] }, { projection: { username: 1, displayName: 1, createdAt: 1 } }).limit(20).toArray();
    res.json({ users: rows.map(safeUser) });
  } catch (e) { next(e); }
});
app.post("/api/conversations", dbReady, auth, async (req, res, next) => {
  try { const row = await getOrCreateConversation(oid(req.userId), oid(clean(req.body.userId, 32))); res.status(201).json({ conversation: { id: String(row._id), members: row.members } }); }
  catch (e) { next(e); }
});
app.get("/api/conversations", dbReady, auth, async (req, res, next) => {
  try {
    const rows = await c.conversations.find({ members: req.userId }).sort({ lastMessageAt: -1, updatedAt: -1 }).limit(100).toArray();
    const otherIds = [...new Set(rows.flatMap(x => x.members).filter(x => x !== req.userId))].map(x => new ObjectId(x));
    const users = await c.users.find({ _id: { $in: otherIds } }, { projection: { username: 1, displayName: 1, createdAt: 1 } }).toArray();
    const map = new Map(users.map(x => [String(x._id), safeUser(x)]));
    res.json({ conversations: rows.map(x => ({ id: String(x._id), peer: map.get(x.members.find(id => id !== req.userId)) || null, lastMessageAt: x.lastMessageAt, updatedAt: x.updatedAt })) });
  } catch (e) { next(e); }
});
app.get("/api/conversations/:id/messages", dbReady, auth, async (req, res, next) => {
  try {
    const conversation = await memberConversation(req.params.id, req.userId);
    const rows = await c.messages.find({ conversationId: conversation._id, expiresAt: { $gt: new Date() } }).sort({ createdAt: 1 }).limit(200).toArray();
    res.json({ messages: rows.map(x => ({ id: String(x._id), senderId: x.senderId, ciphertext: x.ciphertext, iv: x.iv, cryptoVersion: x.cryptoVersion, createdAt: x.createdAt, expiresAt: x.expiresAt })) });
  } catch (e) { next(e); }
});
app.get("/api/calls", dbReady, auth, async (req, res, next) => {
  try {
    const rows = await c.calls.find({ participants: req.userId }).sort({ startedAt: -1 }).limit(100).toArray();
    res.json({ calls: rows.map(x => ({ id: String(x._id), peerId: x.participants.find(id => id !== req.userId), type: x.type, status: x.status, startedAt: x.startedAt, endedAt: x.endedAt, durationSeconds: x.durationSeconds || 0 })) });
  } catch (e) { next(e); }
});

io.use((socket, next) => {
  const raw = socket.handshake.auth && socket.handshake.auth.token;
  if (!raw) { socket.data.userId = null; return next(); } // temporary compatibility for existing call-only clients
  try { socket.data.userId = verify(raw); next(); } catch (_e) { next(new Error("Invalid session")); }
});
io.on("connection", socket => {
  socket.on("private:join", async (p = {}, ack = () => {}) => {
    try { if (!db || !socket.data.userId) throw new Error("Sign in required."); const row = await memberConversation(p.conversationId, socket.data.userId); socket.join("conversation:" + row._id); ack({ ok: true }); }
    catch (e) { ack({ ok: false, error: e.message }); }
  });
  socket.on("private:message", async (p = {}, ack = () => {}) => {
    try {
      if (!db || !socket.data.userId) throw new Error("Sign in required.");
      const row = await memberConversation(p.conversationId, socket.data.userId);
      const ciphertext = clean(p.ciphertext, MAX_MESSAGE_LENGTH), iv = clean(p.iv, 128);
      if (!ciphertext || !iv) throw new Error("Encrypted message payload required.");
      const now = new Date(), expiresAt = new Date(now.getTime() + 7 * 86400000);
      const doc = { conversationId: row._id, senderId: socket.data.userId, ciphertext, iv, cryptoVersion: clean(p.cryptoVersion || "1", 12), createdAt: now, expiresAt };
      const id = (await c.messages.insertOne(doc)).insertedId;
      await c.conversations.updateOne({ _id: row._id }, { $set: { updatedAt: now, lastMessageAt: now } });
      io.to("conversation:" + row._id).emit("private:message", { id: String(id), conversationId: String(row._id), senderId: socket.data.userId, ciphertext, iv, cryptoVersion: doc.cryptoVersion, createdAt: now, expiresAt });
      ack({ ok: true, messageId: String(id), expiresAt });
    } catch (e) { ack({ ok: false, error: e.message }); }
  });
  socket.on("call:log-start", async (p = {}, ack = () => {}) => {
    try {
      if (!db || !socket.data.userId) throw new Error("Sign in required.");
      const row = await memberConversation(p.conversationId, socket.data.userId);
      const type = p.type === "video" ? "video" : "audio";
      const id = (await c.calls.insertOne({ participants: row.members, conversationId: row._id, type, status: "started", startedAt: new Date(), startedBy: socket.data.userId })).insertedId;
      ack({ ok: true, callId: String(id) });
    } catch (e) { ack({ ok: false, error: e.message }); }
  });
  socket.on("call:log-end", async (p = {}, ack = () => {}) => {
    try {
      if (!db || !socket.data.userId || !ObjectId.isValid(p.callId)) throw new Error("Invalid call record.");
      const row = await c.calls.findOne({ _id: new ObjectId(p.callId), participants: socket.data.userId });
      if (!row) throw new Error("Call record not found.");
      const endedAt = new Date();
      await c.calls.updateOne({ _id: row._id }, { $set: { status: "ended", endedAt, durationSeconds: Math.max(0, Math.floor((endedAt - row.startedAt) / 1000)) } });
      ack({ ok: true });
    } catch (e) { ack({ ok: false, error: e.message }); }
  });

  // Compatibility for the currently installed app: keep WebRTC signaling, disable unsafe shared-room chat.
  socket.on("room:join", (p = {}) => {
    const roomId = clean(p.roomId, 80);
    if (!/^[a-zA-Z0-9_-]{1,80}$/.test(roomId)) return socket.emit("server:error", { code: "INVALID_ROOM", message: "Invalid room." });
    socket.join(roomId);
    const peers = Array.from(io.sockets.adapter.rooms.get(roomId) || []).filter(id => id !== socket.id);
    socket.emit("room:joined", { roomId, socketId: socket.id, peers });
    socket.to(roomId).emit("room:user-joined", { socketId: socket.id });
  });
  socket.on("chat:message", () => socket.emit("server:error", { code: "PRIVATE_CHAT_REQUIRED", message: "Shared chat is disabled. Use a private conversation." }));
  for (const event of ["call:offer", "call:answer", "call:ice-candidate", "call:reject", "call:end"]) {
    socket.on(event, (p = {}) => {
      const target = clean(p.targetSocketId, 100);
      if (!target || target === socket.id) return socket.emit("server:error", { code: "INVALID_TARGET", message: "Invalid call target." });
      const { targetSocketId: ignored, ...data } = p;
      io.to(target).emit(event, { ...data, fromSocketId: socket.id, sentAt: new Date().toISOString() });
    });
  }
});
app.use((err, _req, res, _next) => { const status = err.status || 500; if (status >= 500) console.error("API error:", err.message); res.status(status).json({ error: status >= 500 ? "Server error." : err.message }); });

async function start() {
  if (MONGODB_URI) {
    mongoClient = new MongoClient(MONGODB_URI, { serverSelectionTimeoutMS: 10000 });
    await mongoClient.connect();
    db = mongoClient.db(DB_NAME);
    c.users = db.collection("users"); c.conversations = db.collection("conversations"); c.messages = db.collection("messages"); c.calls = db.collection("calls");
    await Promise.all([
      c.users.createIndex({ username: 1 }, { unique: true }),
      c.users.createIndex({ displayName: 1 }),
      c.conversations.createIndex({ members: 1, lastMessageAt: -1 }),
      c.messages.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }),
      c.messages.createIndex({ conversationId: 1, createdAt: 1 }),
      c.calls.createIndex({ participants: 1, startedAt: -1 })
    ]);
    console.log("MongoDB connected to", DB_NAME);
  } else console.warn("MONGODB_URI missing; API is unavailable until configured.");
  server.listen(PORT, "0.0.0.0", () => console.log("PrivateCall listening on", PORT));
}
start().catch(e => { console.error("Startup failed:", e); process.exit(1); });
