#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "Compiling MacTrackpadServer with ScreenCaptureKit video streaming..."
swiftc -O main.swift -framework Cocoa -framework CoreGraphics -framework Network -framework ScreenCaptureKit -framework UniformTypeIdentifiers -o MacTrackpadServer

# Sign with persistent identifier so Accessibility & Screen Recording grants survive across rebuilds
codesign --force --deep --sign - --identifier "com.user.mactrackpadserver" MacTrackpadServer

echo "Compilation successful. Executable created: $DIR/MacTrackpadServer"
