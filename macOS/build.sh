#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "Compiling MacTrackpadServer..."
swiftc -O main.swift -framework Cocoa -framework CoreGraphics -framework Network -o MacTrackpadServer

echo "Compilation successful. Executable created: $DIR/MacTrackpadServer"
