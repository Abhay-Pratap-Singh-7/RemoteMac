#!/bin/bash
PLIST_PATH="$HOME/Library/LaunchAgents/com.user.mactrackpadserver.plist"

echo "Uninstalling MacTrackpadServer auto-start service..."
launchctl bootout "gui/$(id -u)/com.user.mactrackpadserver" 2>/dev/null || true
launchctl unload "$PLIST_PATH" 2>/dev/null || true
rm -f "$PLIST_PATH"
killall -9 MacTrackpadServer 2>/dev/null || pkill -9 -f MacTrackpadServer 2>/dev/null || true

echo "MacTrackpadServer auto-start service completely uninstalled."
