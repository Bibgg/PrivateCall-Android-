# PrivateCall Backend

Node.js + Express + Socket.IO signaling backend for the Android app.

## Run locally

Requires Node.js 20 or newer.

```bash
cd server
npm install
npm start
```

The server listens on `PORT` (default: `3000`). Check:

- `GET /` — service status
- `GET /health` — health check

## Socket events

Client to server:

- `room:join` — `{ "roomId": "room_123" }`
- `chat:message` — `{ "roomId": "room_123", "message": "Hello" }`
- `call:offer`, `call:answer`, `call:ice-candidate`, `call:reject`, `call:end` — include `targetSocketId` and the event data.

Server to client:

- `room:joined`
- `room:user-joined`
- `chat:message`
- `server:error`
- The call signaling events are relayed to the requested target socket.

## Important security note

This is an initial signaling backend, not a production-secure service. It does not yet authenticate users, authorize room membership, persist chat messages, or protect call signaling with account-level permissions. Add authentication, authorization, rate limits, persistent storage, and a TURN service before public release. Do not put passwords, private keys, or database credentials in source control.
