# CinePulse Windows + Chrome controller

This companion lets the CinePulse Android app control a YouTube video playing in Chrome on Windows.

## Setup

1. Keep the Windows laptop and Android phone on the same Wi-Fi network.
2. Install Python 3 on Windows.
3. Open `windows/CinePulseController` and run `start_controller.bat`.
4. Find the laptop's IPv4 address with `ipconfig`. Example: `192.168.1.20`.
5. In Chrome open `chrome://extensions`.
6. Enable **Developer mode**.
7. Choose **Load unpacked** and select `windows/chrome-extension`.
8. Open YouTube in Chrome and start a video.
9. Open CinePulse on Android, enter the laptop IPv4 address and port `8765`, then connect.

## Firewall

If Windows Firewall asks about Python, allow it on **Private networks**. Do not expose port 8765 to the public internet.

## How it works

Android sends play/pause/rewind/forward commands to the Windows controller over the local network. The Chrome extension polls the controller and applies those commands to the active YouTube video element. HDMI is transparent: if the laptop is connected to a TV, the TV shows the laptop's controlled playback.

## Safety/privacy

The controller is intended for the local LAN only. No video is transferred through CinePulse. The Android camera feed is not sent to the laptop.
