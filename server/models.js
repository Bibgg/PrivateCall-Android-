const mongoose = require("mongoose");
const { Schema, model } = mongoose;

const userSchema = new Schema({
  username: { type: String, required: true, trim: true, minlength: 3, maxlength: 24 },
  usernameNormalized: { type: String, required: true, unique: true, index: true },
  email: { type: String, trim: true, lowercase: true, maxlength: 254 },
  emailNormalized: { type: String, lowercase: true, sparse: true, unique: true, index: true },
  passwordHash: { type: String, required: true, select: false },
  displayName: { type: String, trim: true, maxlength: 60 },
  avatarUrl: { type: String, default: "" },
  createdAt: { type: Date, default: Date.now }
}, { versionKey: false });

const conversationSchema = new Schema({
  participantIds: { type: [Schema.Types.ObjectId], required: true, index: true },
  createdAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now },
  lastMessageAt: { type: Date, default: Date.now }
}, { versionKey: false });
conversationSchema.index({ participantIds: 1, updatedAt: -1 });

const messageSchema = new Schema({
  conversationId: { type: Schema.Types.ObjectId, required: true, index: true },
  senderId: { type: Schema.Types.ObjectId, required: true, index: true },
  // Client-side E2EE ciphertext only. Never store plaintext message bodies.
  ciphertext: { type: String, required: true, maxlength: 12000 },
  nonce: { type: String, required: true, maxlength: 256 },
  keyVersion: { type: Number, required: true, min: 1 },
  createdAt: { type: Date, default: Date.now },
  expiresAt: { type: Date, required: true, index: { expires: 0 } }
}, { versionKey: false });
messageSchema.index({ conversationId: 1, createdAt: -1 });

const callSchema = new Schema({
  participantIds: { type: [Schema.Types.ObjectId], required: true, index: true },
  callerId: { type: Schema.Types.ObjectId, required: true },
  calleeId: { type: Schema.Types.ObjectId, required: true },
  kind: { type: String, enum: ["audio", "video"], required: true },
  status: { type: String, enum: ["ringing", "accepted", "rejected", "missed", "ended"], default: "ringing" },
  startedAt: { type: Date, default: Date.now },
  answeredAt: Date,
  endedAt: Date,
  durationSeconds: { type: Number, default: 0 }
}, { versionKey: false });
callSchema.index({ participantIds: 1, startedAt: -1 });

const deviceSchema = new Schema({
  userId: { type: Schema.Types.ObjectId, required: true, index: true },
  deviceId: { type: String, required: true, maxlength: 128 },
  pushToken: { type: String, default: "" },
  updatedAt: { type: Date, default: Date.now }
}, { versionKey: false });
deviceSchema.index({ userId: 1, deviceId: 1 }, { unique: true });

module.exports = {
  User: model("PrivateCallUser", userSchema, "users"),
  Conversation: model("PrivateCallConversation", conversationSchema, "conversations"),
  Message: model("PrivateCallMessage", messageSchema, "messages"),
  Call: model("PrivateCallCall", callSchema, "calls"),
  Device: model("PrivateCallDevice", deviceSchema, "devices")
};
