#!/usr/bin/env bash
# Undo the TCL Google TV (G10) setup: re-enable every package in debloat.txt, bring back the Google TV
# home screen and its setup app, give youtube.com links back to YouTube. The launcher stays installed but
# unused; add --remove-launcher to uninstall it.
#
# The TV must be reachable over ADB (same network, USB debugging on), then:
#   examples/tcl-google-tv-g10/restore.sh      or   TV_HOST=192.168.x.y examples/tcl-google-tv-g10/restore.sh
#
# Without a computer: on the TV, Settings → Apps → See all apps → Show system apps → open each app
# from debloat.txt (plus "Google TV Home") → Enable.
set -euo pipefail
cd "$(dirname "$0")/../.."
[ -n "${TV_HOST:-}" ] || [ -f tools/.tv_host ] || tools/find-tv.sh
tools/restore.sh examples/tcl-google-tv-g10/debloat.txt
# Give youtube.com / youtu.be links back to YouTube instead of SmartTube.
tools/venv/bin/python -I tools/tv.py pm set-app-links-user-selection --user 0 --package org.smarttube.stable false \
  youtube.com www.youtube.com m.youtube.com youtu.be
if [ "${1:-}" = "--remove-launcher" ]; then
  tools/venv/bin/python -I tools/tv.py pm uninstall com.mihir.tvhome
else
  echo "To give the remote's buttons back to TCL: Settings → Accessibility → Remote button remap → off."
  echo "Who's watching profiles stay (and keep their logins): tools/profiles.sh list, tools/profiles.sh remove <id>."
fi
