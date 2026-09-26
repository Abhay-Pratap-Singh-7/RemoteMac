#!/bin/bash
PLIST_PATH="$HOME/Library/LaunchAgents/com.user.mactrackpadserver.plist"

if [ -f "$PLIST_PATH" ]; then
    launchctl unload "$PLIST_PATH" 2>/dev/null || true
    rm -f "$PLIST_PATH"
    pkill -f MacTrackpadServer || true
    echo "MacTrackpadServer auto-start service removed."
else
    echo "Service is not installed."
fi
