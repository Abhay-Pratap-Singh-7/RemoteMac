#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
PLIST_DIR="$HOME/Library/LaunchAgents"
PLIST_PATH="$PLIST_DIR/com.user.mactrackpadserver.plist"
SERVER_BIN="$DIR/MacTrackpadServer.app/Contents/MacOS/MacTrackpadServer"
WORK_DIR="$(dirname "$DIR")"
LOG_PATH="$DIR/server.log"

mkdir -p "$PLIST_DIR"

# Ensure app bundle is built and executable
if [ ! -f "$SERVER_BIN" ]; then
    echo "MacTrackpadServer.app not found. Compiling..."
    "$DIR/build.sh"
fi
chmod +x "$SERVER_BIN"

# Stop existing service and kill any running server instances to prevent port collision
echo "Stopping any existing server instances..."
launchctl bootout "gui/$(id -u)/com.user.mactrackpadserver" 2>/dev/null || true
launchctl unload "$PLIST_PATH" 2>/dev/null || true
killall -9 MacTrackpadServer 2>/dev/null || pkill -9 -f MacTrackpadServer 2>/dev/null || true
sleep 1

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
    <key>LimitLoadToSessionType</key>
    <string>Aqua</string>
    <key>ProcessType</key>
    <string>Interactive</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
    </dict>
    <key>StandardOutPath</key>
    <string>$LOG_PATH</string>
    <key>StandardErrorPath</key>
    <string>$LOG_PATH</string>
</dict>
</plist>
EOF

chmod 644 "$PLIST_PATH"

# Load into GUI user session
launchctl bootstrap "gui/$(id -u)" "$PLIST_PATH" 2>/dev/null || launchctl load -w "$PLIST_PATH"

sleep 1
echo "=========================================="
echo "MacTrackpadServer auto-start service installed & active."
echo "Status: Running continuously in background."
echo "Logs: tail -f $LOG_PATH"
echo "=========================================="
