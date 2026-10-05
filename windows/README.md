# CinePulse Windows + Chrome controller

The Windows companion controls YouTube in Chrome while the phone communicates through an HTTPS cloud relay. No Bluetooth PAN, Wi-Fi discovery, Tailscale or inbound Windows port is required for phone-to-PC control.

## Setup

1. Deploy `relay/worker.js` using `relay/wrangler.toml` to a Cloudflare account you control.
2. Open CinePulse and enter the relay HTTPS URL.
3. Tap **Create secure session**. The phone displays a temporary pairing code.
4. Run `start_controller.bat` on Windows.
5. Enter the same relay URL and pairing code when prompted.
6. In Chrome open `chrome://extensions`, enable Developer mode, and choose **Load unpacked** for `windows/chrome-extension`.
7. Open YouTube and start a video.
8. Use CinePulse controls, attention detection or hand gestures.

## Network

The Windows controller makes outbound HTTPS requests to the relay. Its local `127.0.0.1:8765` bridge is only for the Chrome extension on the same PC.

## Privacy

The Windows controller receives playback commands only. It never receives Android camera frames, face images, eye measurements, microphone audio, video or screen content.
