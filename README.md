# CinePulse

CinePulse is a premium Android movie player built around natural interaction.

## Highlights

- Responsive Jetpack Compose UI for phones and large Android screens.
- Media3/ExoPlayer playback.
- Front-camera, on-device multi-face tracking.
- Eye-open classification support for attention monitoring.
- No camera frames are saved or uploaded.
- Premium dark cinema visual language.
- Branded splash screen: CinePulse — Developed By Harsh Shah.
- Custom launcher icon.
- GitHub Actions builds an installable debug APK.

## Smart controls

This first build establishes the complete responsive player, camera monitoring and privacy architecture. Hand gesture recognition is isolated as the next vision module so it can be added without changing the player or privacy layers.

## Build

Open GitHub Actions, select Build CinePulse APK, open a successful run and download CinePulse-debug.

## Privacy

Camera analysis is performed on-device. The app contains no camera-frame upload path.

## Stack

Kotlin, Jetpack Compose Material 3, CameraX, Google ML Kit Face Detection, AndroidX Media3 / ExoPlayer.
