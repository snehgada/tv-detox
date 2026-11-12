#!/usr/bin/env bash
# Snapshot a TV's package state and settings over ADB, read-only.
# Usage: backup.sh <device> [label]      -> devices/<device>/backup/<label or timestamp>/
# Nothing on the TV is changed. Output is plain text so it can be diffed and
# replayed by restore.sh. The first backup must be labelled 00-original.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_device "${1:-}"
LABEL="${2:-$(date +%Y%m%d-%H%M%S)}"
[[ "$LABEL" =~ ^[A-Za-z0-9._-]{1,40}$ ]] || { echo "bad label" >&2; exit 64; }
OUT="$DEV_DIR/backup/$LABEL"
[[ -e "$OUT" ]] && { echo "$OUT already exists; pick another label" >&2; exit 73; }
mkdir -p "$OUT"
reconnect

echo "Backing up $DEV ($SERIAL) -> $OUT"
sh_ getprop                                   > "$OUT/getprop.txt"
sh_ pm list packages -f -u --show-versioncode > "$OUT/packages-all.txt"
sh_ pm list packages -e                       > "$OUT/packages-enabled.txt"
sh_ pm list packages -d                       > "$OUT/packages-disabled.txt"
sh_ pm list packages -s                       > "$OUT/packages-system.txt"
sh_ pm list packages -3                       > "$OUT/packages-thirdparty.txt"
sh_ pm list packages -u                       > "$OUT/packages-incl-uninstalled.txt"
sh_ pm list packages                          > "$OUT/packages-installed-user0.txt"
for ns in global secure system; do
  sh_ settings list "$ns" > "$OUT/settings-$ns.txt"
done
home_now                                                  > "$OUT/home-activity.txt"
sh_ cmd role get-role-holders android.app.role.HOME > "$OUT/home-role.txt" 2>&1 || true  # absent on Android 11
sh_ wm size                                  > "$OUT/wm-size.txt"
sh_ wm density                               > "$OUT/wm-density.txt"
sh_ dumpsys deviceidle whitelist             > "$OUT/deviceidle-whitelist.txt" 2>&1 || true
sh_ cmd appops query-op RUN_ANY_IN_BACKGROUND ignore > "$OUT/appops-bg-ignored.txt" 2>&1 || true
sh_ dumpsys package preferred-activities     > "$OUT/preferred-activities.txt" 2>&1 || true
sh_ dumpsys meminfo                          > "$OUT/meminfo-before.txt" 2>&1 || true

# Package names, one per line, for restore.sh
sed -n 's/^package://p' "$OUT/packages-enabled.txt"  | sort > "$OUT/enabled.list"
sed -n 's/^package://p' "$OUT/packages-disabled.txt" | sort > "$OUT/disabled.list"

(cd "$OUT" && shasum -a 256 ./*.txt ./*.list > SHA256SUMS)
echo "Done: $(wc -l < "$OUT/enabled.list" | tr -d ' ') enabled, $(wc -l < "$OUT/disabled.list" | tr -d ' ') disabled packages recorded."
