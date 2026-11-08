# PrivateCall Android

Native Android starter for a private chat and WebRTC calling app.

## Build APK on GitHub (no PC required)

1. Open the repository's **Actions** tab.
2. Open **Build Android APK**.
3. Wait for the workflow to finish successfully.
4. Open the completed workflow run and download **PrivateCall-debug-apk** under Artifacts.
5. Extract the ZIP on your Android phone to get `app-debug.apk`.

Every push to `main` triggers a new debug build. You can also run it manually with **Run workflow**.

## Current implementation

- Native Java Android app with chat screen and RecyclerView.
- Socket.IO connection helper.
- WebRTC PeerConnection factory setup and remote video renderer.
- GitHub Actions debug APK build and artifact upload.

## Important before real calls/messages

This is a buildable starter, not a production-ready calling service yet. Real two-way calls require a reachable Socket.IO signaling backend that exchanges offers, answers, and ICE candidates. Production video calls also need local camera/microphone track capture, remote track rendering, TURN servers for restrictive networks, authentication, and secure room authorization. The chat screen currently demonstrates local message UI; connect it to your backend before sending messages between devices.

Set your own HTTPS signaling endpoint in the app when your server is ready. Do not put passwords, API secrets, or private keys in source code.
