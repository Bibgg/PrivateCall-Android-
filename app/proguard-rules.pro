# Keep WebRTC native/JNI classes when minification is enabled.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class io.socket.** { *; }
-dontwarn io.socket.**
