#!/usr/bin/env bash
# Undo everything debloat/launcher setup changed on a TV.
# Usage: restore.sh <device> [--backup LABEL] [--remove-launcher] [--dry-run]
#
#   1. Re-enables every package we disabled (devices/<device>/state/disabled-by-us.txt),
#      plus any package that is disabled now but was enabled in the backup.
#      The setup wizard goes last, once the stock home is back.
#   2. Gives HOME back to the launcher recorded in the backup.
#   3. Restores the screensaver settings from the backup.
#   4. --remove-launcher also uninstalls Calm Home (our own app only).
set -euo pipefail
source "$(dirname "$0")/lib.sh"

DEV_ARG=""; LABEL="00-original"; REMOVE_LAUNCHER=0; DRY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) LABEL="${2:?}"; shift 2 ;;
    --remove-launcher) REMOVE_LAUNCHER=1; shift ;;
    --dry-run) DRY=1; shift ;;
    -*) echo "unknown flag $1" >&2; exit 64 ;;
    *) DEV_ARG="$1"; shift ;;
  esac
done
load_device "$DEV_ARG"
[[ "$LABEL" =~ ^[A-Za-z0-9._-]{1,40}$ ]] || { echo "bad backup label" >&2; exit 64; }
BACKUP="$DEV_DIR/backup/$LABEL"
[[ -f "$BACKUP/enabled.list" ]] || { echo "backup not found in $BACKUP" >&2; exit 66; }
(cd "$BACKUP" && shasum -a 256 -c --quiet SHA256SUMS) || { echo "backup checksum mismatch, refusing" >&2; exit 65; }

# Toggling the setup wizard can drop the adb TCP session, so retry once after reconnecting.
run() {
  if (( DRY )); then echo "[dry-run] adb shell $*"; return 0; fi
  local out
  for attempt in 1 2; do
    if out="$(adb -s "$SERIAL" shell "$@" </dev/null 2>&1)"; then printf '%s\n' "$out" | tr -d '\r'; return 0; fi
    [[ "$(adb -s "$SERIAL" get-state 2>/dev/null)" == device ]] && break  # command failed, not the link
    echo "adb dropped, reconnecting..." >&2; reconnect
  done
  echo "FAILED: adb shell $* -> $out" >&2; return 1
}

reconnect

# --- 1. packages ---------------------------------------------------------
now_disabled="$(sh_ pm list packages -d | sed -n 's/^package://p' | sort)"
want_enabled="$( { cat "$DEV_DIR/state/disabled-by-us.txt" 2>/dev/null || true; comm -12 <(echo "$now_disabled") "$BACKUP/enabled.list"; } | sort -u)"
want_enabled="$(grep -vxF "$SETUP_WIZARD" <<<"$want_enabled" || true)"$'\n'"$SETUP_WIZARD"
echo "== Re-enabling packages on $DEV"
while IFS= read -r pkg; do
  [[ -z "$pkg" ]] && continue
  [[ "$pkg" =~ $PKG_RE ]] || { echo "skip invalid: $pkg"; continue; }
  grep -qxF "$pkg" <<<"$now_disabled" || { echo "ok   $pkg (already enabled)"; continue; }
  grep -qxF "$pkg" "$BACKUP/disabled.list" && { echo "keep $pkg (was disabled before we started)"; continue; }
  run pm enable --user 0 "$pkg" || true
done <<<"$want_enabled"
(( DRY )) || reconnect

# --- 2. home launcher ----------------------------------------------------
orig_home_act="$(tr -d '\r' < "$BACKUP/home-activity.txt" | grep '/' | tail -1)"
orig_home="$(tr -d '\r' < "$BACKUP/home-role.txt" | head -1)"
[[ "$orig_home" =~ $PKG_RE ]] || orig_home="${orig_home_act%%/*}"   # Android 11 has no get-role-holders
echo "== Restoring HOME -> $orig_home"
if [[ "$orig_home" =~ $PKG_RE && "$orig_home_act" =~ $COMP_RE ]]; then
  run cmd role add-role-holder --user 0 android.app.role.HOME "$orig_home" || true
  run cmd package set-home-activity --user 0 "$orig_home_act" || true
else
  echo "could not read original HOME from backup; leaving as is"
fi

# --- 3. screensaver settings --------------------------------------------
echo "== Restoring screensaver settings"
for key in screensaver_components screensaver_default_component screensaver_enabled \
           screensaver_activate_on_sleep screensaver_activate_on_dock; do
  line="$(grep -m1 "^$key=" "$BACKUP/settings-secure.txt" | tr -d '\r' || true)"
  if [[ -z "$line" ]]; then
    echo "delete $key (absent in backup)"
    run settings delete secure "$key" || true
  else
    val="${line#*=}"
    if [[ "$val" =~ ^[A-Za-z0-9_.\$/,-]*$ ]]; then
      run settings put secure "$key" "$val" || true; echo "set $key=$val"
    else
      echo "skip $key: unexpected value in backup"
    fi
  fi
done

# --- 4. optional: remove our launcher -------------------------------------
if (( REMOVE_LAUNCHER )); then
  echo "== Removing $CALM_PKG"
  if sh_ pm list packages | grep -qxF "package:$CALM_PKG"; then
    if (( DRY )); then echo "[dry-run] adb uninstall $CALM_PKG"; else adb -s "$SERIAL" uninstall "$CALM_PKG"; fi
  fi
fi

# --- verify ------------------------------------------------------------------
if (( ! DRY )); then
  echo "== Verifying"
  now_enabled="$(sh_ pm list packages -e | sed -n 's/^package://p' | sort)"
  missing="$(comm -23 "$BACKUP/enabled.list" <(echo "$now_enabled"))"
  if [[ -n "$missing" ]]; then echo "Still disabled vs backup:"; echo "$missing"; exit 1; fi
  echo "All $(wc -l < "$BACKUP/enabled.list" | tr -d ' ') originally-enabled packages are enabled."
  echo "HOME now resolves to: $(home_now)"
  echo "Press Home on the remote (or reboot) to see the original launcher."
fi
