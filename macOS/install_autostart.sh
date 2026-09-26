#!/bin/bash
set -e

PLIST_DIR="$HOME/Library/LaunchAgents"
PLIST_PATH="$PLIST_DIR/com.user.mactrackpadserver.plist"
SERVER_BIN="/Users/abhay/Downloads/HotspotTrackpad/macOS/MacTrackpadServer"
WORK_DIR="/Users/abhay/Downloads/HotspotTrackpad"
LOG_PATH="/Users/abhay/Downloads/HotspotTrackpad/macOS/server.log"

mkdir -p "$PLIST_DIR"

# Unload previous service if exists
launchctl unload "$PLIST_PATH" 2>/dev/null || true

cat <<EOF > "$PLIST_PATH"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.user.mactrackpadserver</string>
    <key>ProgramArguments</key>
    <array>
        <string>$SERVER_BIN</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$WORK_DIR</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$LOG_PATH</string>
    <key>StandardErrorPath</key>
    <string>$LOG_PATH</string>
</dict>
</plist>
EOF

chmod 644 "$PLIST_PATH"
launchctl load "$PLIST_PATH"

echo "MacTrackpadServer installed as permanent auto-start service."
echo "Status: Running in background 24/7 (restarts automatically if stopped)."
echo "Logs: tail -f $LOG_PATH"
