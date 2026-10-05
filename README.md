# CinePulse

CinePulse is a phone-based smart viewing controller. The Android app watches viewers locally and sends playback commands over the same Wi-Fi network to a Windows laptop running Chrome.

## Current architecture

```
Android phone (CinePulse)
  Camera + face/eye attention
          |
          | Wi-Fi
          v
Windows laptop (CinePulse Controller)
          |
          v
Chrome + YouTube
          |
         HDMI
          |
          v
TV
```

If the laptop is connected to a TV by HDMI, the TV automatically shows the controlled YouTube playback.

## Android features

- Premium responsive Jetpack Compose UI.
- Connect to a Windows laptop by IPv4 address.
- Play, pause, rewind 5 seconds and forward 5 seconds commands.
- Front-camera, on-device multi-face tracking.
- If nobody is detected for 2.5 seconds, send pause.
- If all detected viewers keep both eyes closed for 10 seconds, send pause.
- When attention returns, send play.
- Camera frames are not sent to the laptop.

## Windows + Chrome setup

See [windows/README.md](windows/README.md).

The Windows controller is a small Python HTTP server. The Chrome extension polls it locally and controls the YouTube video element.

## Security

Use this only on a trusted private LAN. The controller listens on port 8765 and should not be exposed to the public internet.

## Build

GitHub Actions builds the Android debug APK on pushes to `main`.

## Stack

Kotlin, Jetpack Compose Material 3, CameraX, Google ML Kit Face Detection, AndroidX Media3 dependencies, Python standard-library HTTP server, and a Chrome Manifest V3 extension.


Build verification trigger: Gradle 9.1 / CinePulse APK
