#!/usr/bin/env bash
# Build dist/Crewcast-linux-x64.deb with bundled Java 21 (x64 Linux only).
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

[[ "$(uname -s)" == "Linux" ]] || packaging_die "This script must run on Linux."
[[ "$(uname -m)" == "x86_64" ]] || packaging_die "Linux packaging targets x64 only (found $(uname -m))."

packaging_ensure_java_21

RAW_VERSION="$(packaging_resolve_version "$ROOT")"
APP_VERSION="$(packaging_app_version_triple "$RAW_VERSION")"
echo "==> Version: raw=${RAW_VERSION} jpackage=${APP_VERSION}"

echo "==> Maven build"
packaging_maven_build "$ROOT"

JAR="$(packaging_desktop_jar "$ROOT")"
echo "==> Desktop jar: ${JAR}"

bash "${ROOT}/packaging/generate-artwork.sh"
ICON="${ROOT}/packaging/linux/crewcast.png"
[[ -f "$ICON" ]] || packaging_die "Missing icon: $ICON"

WORKDIR="${ROOT}/packaging/linux/build"
INPUT="${WORKDIR}/input"
RUNTIME="${WORKDIR}/runtime"
rm -rf "$WORKDIR"
mkdir -p "$INPUT" "${ROOT}/dist"
cp "$JAR" "$INPUT/"

echo "==> jdeps + jlink"
MODULES="$(packaging_discover_modules "$JAR")"
echo "    modules: ${MODULES}"
packaging_jlink_runtime "$MODULES" "$RUNTIME"

OUT="${ROOT}/dist/Crewcast-linux-x64.deb"
rm -f "$OUT"

echo "==> jpackage (deb)"
jpackage \
  --type deb \
  --dest "${ROOT}/dist" \
  --name crewcast \
  --app-version "$APP_VERSION" \
  --vendor Solocrew \
  --copyright "Copyright (c) 2026 Solocrew" \
  --icon "$ICON" \
  --input "$INPUT" \
  --main-jar "$(basename "$JAR")" \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --runtime-image "$RUNTIME"

for candidate in "${ROOT}/dist/crewcast_${APP_VERSION}-1_amd64.deb" "${ROOT}/dist/crewcast_"*.deb; do
  if [[ -f "$candidate" && "$candidate" != "$OUT" ]]; then
    mv -f "$candidate" "$OUT"
    break
  fi
done

[[ -f "$OUT" ]] || packaging_die "Expected ${OUT} (check dist/)"

BYTES="$(stat -c%s "$OUT")"
MB="$(awk "BEGIN {printf \"%.1f\", $BYTES / 1048576}")"
echo "Done: ${OUT} (${MB} MiB)"
