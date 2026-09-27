#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "Compiling MacTrackpadServer with ScreenCaptureKit video streaming..."
swiftc -O main.swift -framework Cocoa -framework CoreGraphics -framework Network -framework ScreenCaptureKit -framework UniformTypeIdentifiers -o MacTrackpadServer

# Package into standard macOS Application Bundle so TCC Screen Recording permissions are permanently remembered
APP_DIR="$DIR/MacTrackpadServer.app"
CONTENTS_DIR="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"

mkdir -p "$MACOS_DIR"
cp MacTrackpadServer "$MACOS_DIR/MacTrackpadServer"

cat <<EOF > "$CONTENTS_DIR/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>com.user.mactrackpadserver</string>
    <key>CFBundleName</key>
    <string>MacTrackpadServer</string>
    <key>CFBundleDisplayName</key>
    <string>MacTrackpadServer</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleExecutable</key>
    <string>MacTrackpadServer</string>
    <key>CFBundleVersion</key>
    <string>1.0</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSScreenCaptureUsageDescription</key>
    <string>HotspotTrackpad uses ScreenCaptureKit to stream your Mac screen to your connected mobile device.</string>
</dict>
</plist>
EOF

# Sign both binary and app bundle with stable designated identity
codesign --force --deep --sign - --identifier "com.user.mactrackpadserver" "$MACOS_DIR/MacTrackpadServer"
codesign --force --deep --sign - --identifier "com.user.mactrackpadserver" "$APP_DIR"

echo "Compilation & App Bundle packaging successful: $APP_DIR"
