# PrivateCall Backend v2

Node.js 20+, Express, Socket.IO, and MongoDB Atlas. This branch introduces account-based private conversations and user-addressed call signaling.

## Required Render environment

Set these in the Render service environment (never commit real values):

- `MONGODB_URI`: MongoDB Atlas connection string for the `privatecall` database.
- `JWT_SECRET`: long random secret, at least 32 characters.
- `CLIENT_ORIGIN`: `*` for native Android during initial testing, or explicit browser origins if a web client is added.
- Render supplies `PORT`.

The app exits rather than running in an insecure partially configured state if the database URI or JWT secret is missing.

## HTTP API

- `POST /api/auth/register` — `{ username, email?, password, displayName? }`
- `POST /api/auth/login` — `{ identity, password }` (identity is username or email)
- `GET /api/me` — authenticated profile
- `GET /api/users/search?q=...` — authenticated user search
- `POST /api/conversations` — authenticated `{ userId }`, creates or returns a one-to-one conversation
- `GET /api/conversations` — only the signed-in user's conversations
- `GET /api/conversations/:id/messages` — only messages in a conversation the user belongs to
- `GET /api/calls` — only the signed-in user's call history

Pass the login/register token as `Authorization: Bearer <token>`.

## Authenticated Socket.IO events

Connect with `auth: { token }`.

- `conversation:join` — `{ conversationId }`; server verifies membership before joining the private room.
- `chat:send` — `{ conversationId, ciphertext, nonce, keyVersion }`; server persists ciphertext only and emits `chat:new` to that conversation.
- `call:offer` — `{ targetUserId, kind: "audio"|"video", sdp }`
- `call:answer`, `call:ice-candidate`, `call:reject`, `call:end` — require a valid `callId` and participant membership.

## Data and privacy

Collections: `users`, `conversations`, `messages`, `calls`, and `devices`. Mongoose creates unique account indexes, participant/timeline indexes, and a TTL index on `messages.expiresAt`. New messages receive an expiry timestamp seven days after creation; MongoDB TTL cleanup is asynchronous, so deletion is not guaranteed at the exact second.

The message API accepts encrypted payload fields only. **End-to-end encryption is not complete until the Android app performs audited client-side encryption and key exchange.** Do not send plaintext to `chat:send`.

## Release gate

This branch is not yet compatible with the existing anonymous Android client. The Android login, user search, private conversation, message encryption/decryption, and account-based call signaling screens must be integrated and tested on two physical devices before changing the production Render service. Keep the currently working audio/video service untouched until that validation passes.
