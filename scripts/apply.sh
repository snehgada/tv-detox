#!/usr/bin/env bash
# Apply the whole setup to a TV in the order that is known to be safe:
#   1. phase-1 debloat              (devices/<device>/lists/phase1-disable.txt)
#   2. install Calm Home as HOME     (install-launcher.sh, theme from device.conf)
#   3. disable the setup wizard      (lists/phase2a-setupwizard.txt)  <- must precede 4
#   4. disable the stock home etc.   (lists/phase2-disable.txt)
# Usage: apply.sh <device>      Undo: restore.sh <device>
set -euo pipefail
S="$(cd "$(dirname "$0")" && pwd)"
source "$S/lib.sh"
load_device "${1:-}"

[[ -f "$DEV_DIR/backup/00-original/SHA256SUMS" ]] || { echo "no original backup; run: backup.sh $DEV 00-original" >&2; exit 66; }
reconnect

echo "== 1. phase-1 debloat";  "$S/disable.sh" "$DEV" phase1-disable
echo "== 2. Calm Home ($THEME)"; "$S/install-launcher.sh" "$DEV"
echo "== 3. setup wizard";     "$S/disable.sh" "$DEV" phase2a-setupwizard || true
sleep 3; reconnect             # disabling the wizard can drop the adb session
sh_ pm list packages -d | grep -qxF "package:$SETUP_WIZARD" \
  || { echo "setup wizard still enabled; NOT disabling the stock home (it would loop)" >&2; exit 1; }
echo "== 4. stock home + extras"; "$S/disable.sh" "$DEV" phase2-disable

home="$(home_now)"
echo "HOME resolves to: $home"
[[ "$home" == "$CALM_PKG/.HomeActivity" ]] || { echo "unexpected HOME; run restore.sh $DEV" >&2; exit 1; }
sh_ input keyevent KEYCODE_HOME
echo "Done."
