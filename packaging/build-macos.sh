#!/usr/bin/env bash
# Build dist/Crewcast-macos-<arm64|x64>.dmg (self-contained, host CPU only).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "${ROOT}/packaging/lib/common.sh"

cd "$ROOT"

packaging_require_cmd java
packaging_require_cmd jdeps
packaging_require_cmd jlink
packaging_require_cmd jpackage
packaging_require_cmd jar
packaging_require_cmd mvn
packaging_require_cmd python3

[[ "$(uname -s)" == "Darwin" ]] || packaging_die "This script must run on macOS."

packaging_ensure_java_21

ARCH="$(uname -m)"
case "$ARCH" in
  arm64) ARCH_LABEL="arm64" ;;
  x86_64) ARCH_LABEL="x64" ;;
  *) packaging_die "Unsupported macOS architecture: $ARCH" ;;
esac

RAW_VERSION="$(packaging_raw_version "$ROOT")"
APP_VERSION="$(packaging_app_version_macos "$RAW_VERSION")"
echo "==> Version: raw=${RAW_VERSION} jpackage=${APP_VERSION}"

echo "==> Maven build"
packaging_maven_build "$ROOT"

JAR="$(packaging_desktop_jar "$ROOT")"
[[ -f "$JAR" ]] || packaging_die "Runnable jar not found: $JAR"

bash "${ROOT}/packaging/generate-artwork.sh"
ICNS="${ROOT}/packaging/macos/Crewcast.icns"
if [[ ! -f "$ICNS" ]]; then
  packaging_require_cmd swift
  packaging_require_cmd iconutil
  packaging_require_cmd sips
  bash "${ROOT}/packaging/macos/generate-icns.sh"
fi

WORKDIR="${ROOT}/packaging/macos/build"
INPUT="${WORKDIR}/input"
RUNTIME="${WORKDIR}/runtime"
rm -rf "$WORKDIR"
mkdir -p "$INPUT" "${ROOT}/dist"
cp "$JAR" "$INPUT/"

echo "==> jdeps + jlink"
MODULES="$(packaging_discover_modules "$JAR")"
echo "    modules: ${MODULES}"
packaging_jlink_runtime "$MODULES" "$RUNTIME"

OUT="${ROOT}/dist/Crewcast-macos-${ARCH_LABEL}.dmg"
rm -f "$OUT"

echo "==> jpackage (dmg)"
jpackage \
  --type dmg \
  --dest "${ROOT}/dist" \
  --name Crewcast \
  --app-version "$APP_VERSION" \
  --vendor Solocrew \
  --copyright "Copyright (c) 2026 Solocrew" \
  --mac-package-identifier com.solocrew.crewcast \
  --icon "$ICNS" \
  --input "$INPUT" \
  --main-jar "$(basename "$JAR")" \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --runtime-image "$RUNTIME"

# Normalize jpackage output name to our fixed artifact name.
for candidate in "${ROOT}/dist/Crewcast-${APP_VERSION}.dmg" "${ROOT}/dist/Crewcast.dmg"; do
  if [[ -f "$candidate" && "$candidate" != "$OUT" ]]; then
    mv -f "$candidate" "$OUT"
    break
  fi
done

[[ -f "$OUT" ]] || packaging_die "Expected ${OUT} (check dist/)"

BYTES="$(stat -f%z "$OUT" 2>/dev/null || stat -c%s "$OUT")"
MB="$(awk "BEGIN {printf \"%.1f\", $BYTES / 1048576}")"
echo "Done: ${OUT} (${MB} MiB)"
