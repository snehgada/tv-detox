#!/usr/bin/env bash
# Install the device's Calm Home theme, AOT-compile it, make it the HOME app and
# select its screensaver.
# Usage: install-launcher.sh <device>    (theme comes from devices/<device>/device.conf)
# Build first: launcher/build.sh <theme>. Undo with restore.sh.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_device "${1:-}"
APK="$ROOT/launcher/build/calm-home-$THEME.apk"
[[ -f "$APK" ]] || { echo "build first: $ROOT/launcher/build.sh $THEME" >&2; exit 66; }

reconnect
adb -s "$SERIAL" install -r --no-incremental "$APK"
sh_ cmd package compile -m speed -f "$CALM_PKG"          # AOT: ~0.6 s cold start instead of ~3.8 s
sh_ cmd role add-role-holder --user 0 android.app.role.HOME "$CALM_PKG" || true
sh_ cmd package set-home-activity --user 0 "$CALM_PKG/.HomeActivity" || true
sh_ settings put secure screensaver_components "$CALM_PKG/.CalmDream"

echo "Installed:     $(sh_ dumpsys package "$CALM_PKG" | grep -m1 -oE 'versionName=[^ ]+')"
echo "HOME resolves: $(home_now)   (stock home wins until it is disabled)"
echo "Screensaver:   $(sh_ settings get secure screensaver_components)"
