# PrivateCall Android

Native Android chat and WebRTC calling app.

## Build APK on GitHub (no PC required)

1. Open the repository's **Actions** tab.
2. Open **Build Android APK**.
3. Wait for the workflow to finish successfully.
4. Open the completed workflow run and download **PrivateCall-debug-apk** under Artifacts.
5. Extract the ZIP on your Android phone to get `app-debug.apk`.

Pushes to `main`, pull requests targeting `main`, and manual workflow runs build a debug APK.

## Current app features

- Native Java Android chat screen and RecyclerView.
- Socket.IO room connection and chat signaling.
- WebRTC audio and camera video tracks.
- Full-screen remote video with local camera preview.
- Front/back camera switch, camera mute, microphone mute.
- Incoming call accept/reject and end-call controls.
- GitHub Actions debug APK build and artifact upload.

## Calling backend

The Node.js Socket.IO backend relays chat and WebRTC signaling events. Audio/video media is sent peer-to-peer; the signaling server does not carry the media stream.

The Android client currently has a Google STUN server configured. STUN alone cannot guarantee calls through every carrier NAT, restrictive Wi-Fi, or firewall. For reliable calls on difficult networks, configure a TURN service and add its authenticated ICE server credentials to the app's RTC configuration. Do not commit TURN passwords or other secrets to source control.

## Important testing and production notes

This repository is an evolving starter, not yet a production-ready calling service. Test calls on two physical Android phones, including different mobile networks. Confirm camera/microphone permissions, incoming-call behavior, ICE connectivity, and reconnect behavior.

Before public release, add user authentication, room authorization, signaling rate limits, secure TURN credentials, and privacy/security review. Do not put passwords, API secrets, or private keys in source code.
