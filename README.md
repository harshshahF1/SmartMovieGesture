# CinePulse

CinePulse is a phone-based smart viewing controller for Chrome + YouTube. The Android phone performs face/eye attention analysis and hand-gesture recognition locally. Only playback commands are sent through an HTTPS cloud relay to the Windows controller.

## Architecture

Android phone / CinePulse -> HTTPS command-only relay -> Windows CinePulse Controller -> Chrome + YouTube

## Privacy boundary

- Camera frames never leave the Android device.
- Face images, face measurements and eye-state data never leave the Android device.
- Microphone/audio is not used or uploaded by CinePulse.
- Video, screenshots and YouTube content are never uploaded to the relay.
- The relay carries only play, pause, rewind and forward commands plus temporary session routing data.
- The relay stores only short-lived session state needed to route commands; it has no endpoint for camera/audio/video uploads.

The cloud infrastructure can still see ordinary network metadata such as connection source information. The privacy guarantee here is that CinePulse does not send personal media, biometric data, audio or video to the relay.

## Cloud relay

`relay/worker.js` is a Cloudflare Worker using a Durable Object. Deploy it to a Cloudflare account you control. The Android app creates a temporary pairing code; enter that code in the Windows controller. The controller then polls the relay for commands.

No Tailscale, Bluetooth PAN, Wi-Fi discovery, public Windows port, camera upload, audio upload or video upload is required.

## Build

GitHub Actions builds the Android debug APK on pushes to `main`.

## Features

- Play / pause / rewind 5 seconds / forward 5 seconds.
- Multi-viewer face and eye attention detection on-device.
- Nobody detected for 2.5 seconds -> pause.
- Everyone's eyes closed for 10 seconds -> pause.
- Attention returns -> play.
- Left fist gesture -> rewind 5 seconds.
- Right fist gesture -> forward 5 seconds.
