#!/usr/bin/env bash
# Builds packaging/macos/Crewcast.icns from a simple accent-colored app icon.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ICONSET="${ROOT}/packaging/macos/Crewcast.iconset"
ICNS="${ROOT}/packaging/macos/Crewcast.icns"
SWIFT="${ROOT}/packaging/macos/IconDraw.swift"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "generate-icns.sh requires macOS (iconutil)." >&2
  exit 1
fi

command -v iconutil >/dev/null 2>&1 || { echo "iconutil not found." >&2; exit 1; }
command -v sips >/dev/null 2>&1 || { echo "sips not found." >&2; exit 1; }

rm -rf "$ICONSET"
mkdir -p "$ICONSET"

BASE="${ICONSET}/base-1024.png"
swift "$SWIFT" 1024 "$BASE"

add_icon() {
  local name="$1"
  local dim="$2"
  cp "$BASE" "${ICONSET}/${name}"
  sips -z "$dim" "$dim" "${ICONSET}/${name}" >/dev/null
}

add_icon icon_16x16.png 16
add_icon icon_16x16@2x.png 32
add_icon icon_32x32.png 32
add_icon icon_32x32@2x.png 64
add_icon icon_128x128.png 128
add_icon icon_128x128@2x.png 256
add_icon icon_256x256.png 256
add_icon icon_256x256@2x.png 512
add_icon icon_512x512.png 512
add_icon icon_512x512@2x.png 1024

rm -f "$ICNS"
iconutil -c icns "$ICONSET" -o "$ICNS"
echo "Wrote ${ICNS}"
