#!/usr/bin/env bash
# Optional, macOS: replace the launcher's wallpapers with stills from Apple TV's aerial screensaver.
# Grabs one 1920x1080 frame per video with AVFoundation (range reads, ~1-2 MB each, not whole videos).
# Apple's images are for your own TV only: don't commit or redistribute them.
# Undo: git checkout launcher/assets/wallpapers && git clean -f launcher/assets/wallpapers
# Usage: tools/aerial-wallpapers.sh ["Greenland" "Yosemite" ...]   (default: 15 landscapes and space shots)
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
swiftc -O -o "$tmp/grab" tools/aerial-grab.swift
curl -sSf -o "$tmp/res.tar" https://sylvan.apple.com/Aerials/resources-16.tar
tar -xf "$tmp/res.tar" -C "$tmp" entries.json
[ $# -gt 0 ] || set -- "Greenland" "Iceland" "Yosemite" "Grand Canyon" "Patagonia" "Hawaii" "New Zealand" \
  "Scotland" "Tahiti Waves" "Liwa" "Antarctica" "Caribbean Day" "San Francisco" "New York Night" "North America Aurora"
rm -f launcher/assets/wallpapers/*.jpg
n=0
for label in "$@"; do
  url=$(/usr/bin/python3 -I -c 'import json,sys
for a in json.load(open(sys.argv[1]))["assets"]:
    if a["accessibilityLabel"] == sys.argv[2]: print(a["url-1080-SDR"]); break' "$tmp/entries.json" "$label")
  [ -n "$url" ] || { echo "no aerial called $label"; continue; }
  n=$((n + 1))
  out=$(printf 'launcher/assets/wallpapers/%02d.jpg' "$n")
  "$tmp/grab" "$url" "$out" 0.4 && echo "✓ $label" || { echo "✗ $label"; rm -f "$out"; n=$((n - 1)); }
done
echo "$n wallpapers. Next: launcher/build-and-install.sh"
