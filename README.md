# Hotspot Trackpad (Android to Mac)

Control your Mac cursor and gestures from an Android device connected via Mobile Hotspot or local Wi-Fi.

## Architecture
- **macOS Receiver (`/macOS`)**: Lightweight menu-bar background daemon in Swift listening for UDP packets on port `8080` and driving native macOS cursor movements and clicks using `CoreGraphics` (`CGEvent`).
- **Android Client (`/android`)**: Modern Jetpack Compose Android app with auto-discovery broadcast and low-latency UDP event streaming.

## Running macOS Receiver
1. Grant Accessibility permissions to Terminal / your app under:
   `System Settings > Privacy & Security > Accessibility`.
2. Build and run:
   ```bash
   cd macOS
   ./build.sh
   ./MacTrackpadServer
   ```
   A `📱 Trackpad` item will appear in your Mac menu bar.

## Running Android App
1. Connect your Mac to your Android phone's Hotspot (or have both on the same Wi-Fi).
2. Open `/android` in Android Studio and run on your Android device.
3. The app will automatically discover your Mac on port `8080`.
