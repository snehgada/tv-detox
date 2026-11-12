# Shared helpers for the per-TV scripts. Source this after `set -euo pipefail`.
# Every script takes a device name: a folder under devices/ containing device.conf:
#   serial=192.168.29.87:5555
#   theme=anime            (a folder under launcher/themes/)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG_RE='^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$'
COMP_RE='^[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+$'
CALM_PKG="home.calm.tv"
SETUP_WIZARD="com.google.android.tungsten.setupwraith"

devices() { ls "$ROOT/devices" | tr '\n' ' '; }

# Sets DEV, DEV_DIR, SERIAL, THEME. Values are validated; device.conf is parsed, never sourced.
load_device() {
  DEV="${1:-}"
  if [[ ! "$DEV" =~ ^[a-z0-9][a-z0-9-]{0,40}$ || ! -f "$ROOT/devices/$DEV/device.conf" ]]; then
    echo "unknown device '${DEV}'. Known: $(devices)" >&2; exit 64
  fi
  DEV_DIR="$ROOT/devices/$DEV"
  SERIAL="$(conf_get serial)"
  THEME="$(conf_get theme)"
  [[ "$SERIAL" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}:[0-9]{1,5}$ ]] || { echo "bad serial in $DEV_DIR/device.conf" >&2; exit 65; }
  [[ "$THEME" =~ ^[a-z][a-z0-9-]{0,30}$ && -d "$ROOT/launcher/themes/$THEME" ]] || { echo "bad theme in $DEV_DIR/device.conf" >&2; exit 65; }
}

conf_get() { sed -n "s/^$1=//p" "$DEV_DIR/device.conf" | head -1 | tr -d '\r[:space:]'; }

reconnect() {
  for _ in $(seq 1 30); do
    adb connect "$SERIAL" >/dev/null 2>&1 || true
    [[ "$(adb -s "$SERIAL" get-state 2>/dev/null)" == device ]] && return 0
    sleep 3
  done
  echo "$DEV ($SERIAL) not reachable over adb" >&2; return 1
}

# adb shell with stdin closed (so it can't eat a while-read loop) and CRs stripped.
sh_() { adb -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }

home_now() { sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }

# Screenshot via a file on the device: some MediaTek TVs print debug text before the PNG on stdout.
shot() {
  local name="$1"
  [[ "$name" =~ ^[A-Za-z0-9._-]{1,60}$ ]] || { echo "bad screenshot name" >&2; return 1; }
  mkdir -p "$DEV_DIR/screens"
  sh_ screencap -p /sdcard/calm-shot.png >/dev/null 2>&1 || true
  adb -s "$SERIAL" pull /sdcard/calm-shot.png "$DEV_DIR/screens/$name.png" >/dev/null 2>&1 || true
  sh_ rm -f /sdcard/calm-shot.png >/dev/null 2>&1 || true
  echo "$DEV_DIR/screens/$name.png"
}
