const http = require("node:http");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");
const mongoose = require("mongoose");
const jwt = require("jsonwebtoken");
const bcrypt = require("bcryptjs");
const { Server } = require("socket.io");
const { User, Conversation, Message, Call } = require("./models");

const app = express();
const server = http.createServer(app);
const PORT = Number(process.env.PORT || 3000);
const MONGODB_URI = process.env.MONGODB_URI;
const JWT_SECRET = process.env.JWT_SECRET;
const origins = (process.env.CLIENT_ORIGIN || "*").split(",").map(v => v.trim());
if (!MONGODB_URI || !JWT_SECRET || JWT_SECRET.length < 32) {
  throw new Error("Set MONGODB_URI and JWT_SECRET (minimum 32 characters) in the host environment.");
}
const corsOrigin = origins.includes("*") ? "*" : origins;
app.disable("x-powered-by");
app.use(helmet());
app.use(cors({ origin: corsOrigin }));
app.use(express.json({ limit: "32kb" }));
app.use("/api/auth", rateLimit({ windowMs: 900000, limit: 30, standardHeaders: true, legacyHeaders: false }));
app.use("/api", rateLimit({ windowMs: 60000, limit: 120, standardHeaders: true, legacyHeaders: false }));

app.get("/", (_req, res) => res.json({ name: "PrivateCall API", status: "online", version: 2 }));
app.get("/health", (_req, res) => {
  const ready = mongoose.connection.readyState === 1;
  res.status(ready ? 200 : 503).json({ ok: ready, database: ready ? "connected" : "disconnected" });
});

const escapeRegex = value => value.replace(/[-/\\^$*+?.()|[\]{}]/g, "\\$&");
const clean = (value, max = 100) => typeof value === "string" ? value.trim().slice(0, max) : "";
const validId = value => mongoose.isValidObjectId(value);
const publicUser = u => ({ id: String(u._id), username: u.username, displayName: u.displayName || u.username, avatarUrl: u.avatarUrl || "" });
const tokenFor = user => jwt.sign({ sub: String(user._id) }, JWT_SECRET, { expiresIn: "30d", issuer: "privatecall" });

function requireAuth(req, res, next) {
  try {
    const h = req.headers.authorization || "";
    if (!h.startsWith("Bearer ")) throw new Error("missing");
    const payload = jwt.verify(h.slice(7), JWT_SECRET, { issuer: "privatecall" });
    req.userId = payload.sub;
    next();
  } catch { res.status(401).json({ error: "AUTH_REQUIRED" }); }
}
async function participantConversation(id, userId) {
  if (!validId(id)) return null;
  return Conversation.findOne({ _id: id, participantIds: userId });
}

app.post("/api/auth/register", async (req, res) => {
  try {
    const username = clean(req.body.username, 24);
    const email = clean(req.body.email, 254).toLowerCase();
    const password = typeof req.body.password === "string" ? req.body.password : "";
    if (!/^[a-zA-Z0-9_.]{3,24}$/.test(username) || password.length < 10 || password.length > 128) {
      return res.status(400).json({ error: "INVALID_INPUT", message: "Username: 3–24 letters/numbers/._ ; password: 10–128 characters." });
    }
    if (email && !/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email)) return res.status(400).json({ error: "INVALID_EMAIL" });
    const usernameNormalized = username.toLowerCase();
    const exists = await User.findOne({ $or: [{ usernameNormalized }, ...(email ? [{ emailNormalized: email }] : [])] });
    if (exists) return res.status(409).json({ error: "ACCOUNT_EXISTS" });
    const passwordHash = await bcrypt.hash(password, 12);
    const user = await User.create({ username, usernameNormalized, email: email || undefined, emailNormalized: email || undefined, passwordHash, displayName: clean(req.body.displayName || username, 60) });
    res.status(201).json({ token: tokenFor(user), user: publicUser(user) });
  } catch (e) {
    if (e && e.code === 11000) return res.status(409).json({ error: "ACCOUNT_EXISTS" });
    console.error("register", e); res.status(500).json({ error: "SERVER_ERROR" });
  }
});

app.post("/api/auth/login", async (req, res) => {
  try {
    const identity = clean(req.body.identity, 254).toLowerCase();
    const password = typeof req.body.password === "string" ? req.body.password : "";
    const user = await User.findOne({ $or: [{ usernameNormalized: identity }, { emailNormalized: identity }] }).select("+passwordHash");
    if (!user || !(await bcrypt.compare(password, user.passwordHash))) return res.status(401).json({ error: "INVALID_CREDENTIALS" });
    res.json({ token: tokenFor(user), user: publicUser(user) });
  } catch (e) { console.error("login", e); res.status(500).json({ error: "SERVER_ERROR" }); }
});

app.get("/api/me", requireAuth, async (req, res) => {
  const user = await User.findById(req.userId);
  if (!user) return res.status(404).json({ error: "USER_NOT_FOUND" });
  res.json({ user: publicUser(user) });
});

app.get("/api/users/search", requireAuth, async (req, res) => {
  const q = clean(req.query.q, 40).toLowerCase();
  if (q.length < 2) return res.json({ users: [] });
  const pattern = new RegExp("^" + escapeRegex(q), "i");
  const users = await User.find({ _id: { $ne: req.userId }, $or: [{ usernameNormalized: pattern }, { displayName: pattern }] }).limit(20);
  res.json({ users: users.map(publicUser) });
});

app.post("/api/conversations", requireAuth, async (req, res) => {
  const otherId = clean(req.body.userId, 40);
  if (!validId(otherId) || otherId === req.userId) return res.status(400).json({ error: "INVALID_USER" });
  if (!await User.exists({ _id: otherId })) return res.status(404).json({ error: "USER_NOT_FOUND" });
  const participantIds = [req.userId, otherId].sort();
  let c = await Conversation.findOne({ participantIds: { $all: participantIds, $size: 2 } });
  if (!c) c = await Conversation.create({ participantIds });
  res.json({ conversation: { id: String(c._id), updatedAt: c.updatedAt } });
});

app.get("/api/conversations", requireAuth, async (req, res) => {
  const rows = await Conversation.find({ participantIds: req.userId }).sort({ updatedAt: -1 }).limit(100);
  const conversations = await Promise.all(rows.map(async c => {
    const peerId = c.participantIds.find(id => String(id) !== req.userId);
    const peer = peerId ? await User.findById(peerId) : null;
    const last = await Message.findOne({ conversationId: c._id, expiresAt: { $gt: new Date() } }).sort({ createdAt: -1 });
    return { id: String(c._id), peer: peer ? publicUser(peer) : null, lastMessage: last ? {
      id: String(last._id), senderId: String(last.senderId), ciphertext: last.ciphertext, nonce: last.nonce, keyVersion: last.keyVersion, createdAt: last.createdAt
    } : null, updatedAt: c.updatedAt };
  }));
  res.json({ conversations });
});

app.get("/api/conversations/:id/messages", requireAuth, async (req, res) => {
  const c = await participantConversation(req.params.id, req.userId);
  if (!c) return res.status(404).json({ error: "CONVERSATION_NOT_FOUND" });
  const before = req.query.before ? new Date(req.query.before) : new Date();
  const rows = await Message.find({ conversationId: c._id, expiresAt: { $gt: new Date() }, createdAt: { $lt: before } }).sort({ createdAt: -1 }).limit(50);
  res.json({ messages: rows.reverse().map(m => ({ id: String(m._id), senderId: String(m.senderId), ciphertext: m.ciphertext, nonce: m.nonce, keyVersion: m.keyVersion, createdAt: m.createdAt })) });
});

app.get("/api/calls", requireAuth, async (req, res) => {
  const rows = await Call.find({ participantIds: req.userId }).sort({ startedAt: -1 }).limit(100);
  res.json({ calls: rows.map(c => ({ id: String(c._id), callerId: String(c.callerId), calleeId: String(c.calleeId), kind: c.kind, status: c.status, startedAt: c.startedAt, answeredAt: c.answeredAt, endedAt: c.endedAt, durationSeconds: c.durationSeconds })) });
});

const io = new Server(server, { cors: { origin: corsOrigin, methods: ["GET", "POST"] }, maxHttpBufferSize: 256 * 1024, connectTimeout: 20000 });
io.use((socket, next) => {
  try {
    const token = socket.handshake.auth && socket.handshake.auth.token;
    const payload = jwt.verify(token, JWT_SECRET, { issuer: "privatecall" });
    socket.userId = payload.sub; next();
  } catch { next(new Error("AUTH_REQUIRED")); }
});

io.on("connection", socket => {
  socket.join("user:" + socket.userId);
  socket.on("conversation:join", async (payload = {}, ack = () => {}) => {
    const c = await participantConversation(payload.conversationId, socket.userId);
    if (!c) return ack({ ok: false, error: "NOT_A_PARTICIPANT" });
    socket.join("conversation:" + String(c._id)); ack({ ok: true });
  });
  socket.on("chat:send", async (payload = {}, ack = () => {}) => {
    try {
      const c = await participantConversation(payload.conversationId, socket.userId);
      if (!c) return ack({ ok: false, error: "NOT_A_PARTICIPANT" });
      const ciphertext = clean(payload.ciphertext, 12000), nonce = clean(payload.nonce, 256), keyVersion = Number(payload.keyVersion);
      if (!ciphertext || !nonce || !Number.isInteger(keyVersion) || keyVersion < 1) return ack({ ok: false, error: "INVALID_ENCRYPTED_MESSAGE" });
      const now = new Date();
      const m = await Message.create({ conversationId: c._id, senderId: socket.userId, ciphertext, nonce, keyVersion, createdAt: now, expiresAt: new Date(now.getTime() + 7 * 86400000) });
      c.updatedAt = now; c.lastMessageAt = now; await c.save();
      const safe = { id: String(m._id), conversationId: String(c._id), senderId: socket.userId, ciphertext, nonce, keyVersion, createdAt: now };
      io.to("conversation:" + String(c._id)).emit("chat:new", safe); ack({ ok: true, message: safe });
    } catch (e) { console.error("chat:send", e); ack({ ok: false, error: "SERVER_ERROR" }); }
  });
  socket.on("call:offer", async (p = {}, ack = () => {}) => {
    try {
      const targetUserId = clean(p.targetUserId, 40);
      if (!validId(targetUserId) || targetUserId === socket.userId || !["audio", "video"].includes(p.kind)) return ack({ ok: false, error: "INVALID_CALL" });
      if (!await User.exists({ _id: targetUserId })) return ack({ ok: false, error: "USER_NOT_FOUND" });
      const c = await Call.create({ participantIds: [socket.userId, targetUserId], callerId: socket.userId, calleeId: targetUserId, kind: p.kind, status: "ringing" });
      io.to("user:" + targetUserId).emit("call:offer", { callId: String(c._id), fromUserId: socket.userId, kind: p.kind, sdp: clean(p.sdp, 50000) });
      ack({ ok: true, callId: String(c._id) });
    } catch (e) { console.error("call offer", e); ack({ ok: false, error: "SERVER_ERROR" }); }
  });
  for (const eventName of ["call:answer", "call:ice-candidate", "call:reject", "call:end"]) {
    socket.on(eventName, async (p = {}, ack = () => {}) => {
      try {
        if (!validId(p.callId)) return ack({ ok: false, error: "INVALID_CALL" });
        const c = await Call.findOne({ _id: p.callId, participantIds: socket.userId });
        if (!c) return ack({ ok: false, error: "CALL_NOT_FOUND" });
        const peerId = String(c.callerId) === socket.userId ? String(c.calleeId) : String(c.callerId);
        if (eventName === "call:answer") { c.status = "accepted"; c.answeredAt = new Date(); await c.save(); }
        if (eventName === "call:reject") { c.status = "rejected"; c.endedAt = new Date(); await c.save(); }
        if (eventName === "call:end") { c.status = "ended"; c.endedAt = new Date(); c.durationSeconds = c.answeredAt ? Math.max(0, Math.floor((c.endedAt - c.answeredAt) / 1000)) : 0; await c.save(); }
        const forwarded = { ...p, fromUserId: socket.userId }; delete forwarded.targetSocketId;
        io.to("user:" + peerId).emit(eventName, forwarded); ack({ ok: true });
      } catch (e) { console.error(eventName, e); ack({ ok: false, error: "SERVER_ERROR" }); }
    });
  }
  socket.on("disconnect", reason => console.log("socket disconnected", socket.userId, reason));
});

mongoose.connect(MONGODB_URI, { serverSelectionTimeoutMS: 15000 })
  .then(async () => {
    await Promise.all([User.init(), Conversation.init(), Message.init(), Call.init()]);
    server.listen(PORT, "0.0.0.0", () => console.log("PrivateCall API listening on", PORT));
  })
  .catch(error => { console.error("MongoDB connection failed", error); process.exit(1); });
