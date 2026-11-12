#!/usr/bin/env bash
# Disable the packages named in one of a device's lists, for user 0. Never uninstalls.
# Usage: disable.sh <device> <list>        list = devices/<device>/lists/<list>.txt
# Every package actually disabled is appended to devices/<device>/state/disabled-by-us.txt,
# which restore.sh reads.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_device "${1:-}"
NAME="${2:?usage: disable.sh <device> <list>   (lists: $(ls "$DEV_DIR/lists" | sed 's/\.txt$//' | tr '\n' ' '))}"
[[ "$NAME" =~ ^[A-Za-z0-9_-]{1,40}$ && -f "$DEV_DIR/lists/$NAME.txt" ]] || { echo "no such list: $NAME" >&2; exit 66; }
LIST="$DEV_DIR/lists/$NAME.txt"
STATE="$DEV_DIR/state/disabled-by-us.txt"
mkdir -p "$(dirname "$STATE")"; touch "$STATE"

reconnect
installed="$(sh_ pm list packages -u | sed -n 's/^package://p')"
disabled="$(sh_ pm list packages -d | sed -n 's/^package://p')"

rc=0
while IFS= read -r line; do
  pkg="${line%%#*}"; pkg="$(echo "$pkg" | tr -d '[:space:]')"
  [[ -z "$pkg" ]] && continue
  if [[ ! "$pkg" =~ $PKG_RE ]]; then echo "SKIP invalid name: $pkg"; rc=1; continue; fi
  if ! grep -qxF "$pkg" <<<"$installed"; then echo "SKIP not installed: $pkg"; continue; fi
  if grep -qxF "$pkg" <<<"$disabled"; then echo "ok   already disabled: $pkg"; continue; fi
  out="$(adb -s "$SERIAL" shell pm disable-user --user 0 "$pkg" </dev/null 2>&1 | tr -d '\r')"
  if grep -q 'new state: disabled-user' <<<"$out"; then
    echo "DONE $pkg"
    grep -qxF "$pkg" "$STATE" || echo "$pkg" >> "$STATE"
  else
    echo "FAIL $pkg: $out"; rc=1
  fi
done < "$LIST"
exit $rc
