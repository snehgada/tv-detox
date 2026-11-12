#!/usr/bin/env bash
# Screenshot a TV into devices/<device>/screens/<name>.png
# Usage: shot.sh <device> <name>
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_device "${1:-}"
reconnect
shot "${2:?usage: shot.sh <device> <name>}"
