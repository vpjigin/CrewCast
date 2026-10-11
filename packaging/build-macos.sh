#!/usr/bin/env bash
# Build a self-contained macOS .dmg for Crewcast (host CPU only: arm64 or x64).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

require_cmd java
require_cmd jdeps
require_cmd jlink
require_cmd jpackage
require_cmd jar
require_cmd mvn
require_cmd swift
require_cmd iconutil
require_cmd sips

if [[ "$(uname -s)" != "Darwin" ]]; then
  die "This script must run on macOS."
fi

JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [[ -z "${JAVA_MAJOR}" ]] || (( JAVA_MAJOR < 21 )); then
  die "JDK 21 or newer is required (found Java ${JAVA_MAJOR:-unknown}). Install a JDK 21+ with jlink and jpackage."
fi

ARCH="$(uname -m)"
case "$ARCH" in
  arm64) ARCH_LABEL="arm64" ;;
  x86_64) ARCH_LABEL="x64" ;;
  *) die "Unsupported macOS architecture: $ARCH" ;;
esac

echo "==> Maven build (desktop + dependencies, skip tests)"
mvn -pl desktop -am package -DskipTests

MVN_VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
FILE_VERSION="${MVN_VERSION%-SNAPSHOT}"
JAR="${ROOT}/desktop/target/crewcast-desktop-${MVN_VERSION}.jar"
[[ -f "$JAR" ]] || die "Runnable jar not found: $JAR"

# jpackage on macOS requires CFBundleShortVersionString with a major version >= 1.
# Maven 0.0.x-SNAPSHOT is mapped to 1.0.0 until the project reaches 1.x.
jpackage_app_version() {
  local v="$1"
  local major="${v%%.*}"
  if [[ "$major" =~ ^[0-9]+$ ]] && (( major < 1 )); then
    echo "1.0.0"
  else
    echo "$v"
  fi
}
APP_VERSION="$(jpackage_app_version "$FILE_VERSION")"
if [[ "$APP_VERSION" != "$FILE_VERSION" ]]; then
  echo "Note: Maven version ${FILE_VERSION} → jpackage --app-version ${APP_VERSION} (macOS requires major >= 1)."
fi

ICNS="${ROOT}/packaging/macos/Crewcast.icns"
if [[ ! -f "$ICNS" ]]; then
  echo "==> Generating app icon"
  bash "${ROOT}/packaging/macos/generate-icns.sh"
fi

WORKDIR="${ROOT}/packaging/macos/build"
INPUT="${WORKDIR}/input"
RUNTIME="${WORKDIR}/runtime"
rm -rf "$WORKDIR"
mkdir -p "$INPUT"
cp "$JAR" "$INPUT/"

echo "==> Discovering Java modules (jdeps)"
JDEPS_TMP="$(mktemp -d)"
trap 'rm -rf "$JDEPS_TMP"' EXIT
( cd "$JDEPS_TMP" && jar xf "$JAR" )
JDEPS_MODULES="$(
  jdeps --ignore-missing-deps --multi-release 21 --recursive --print-module-deps \
    --class-path "${JDEPS_TMP}/BOOT-INF/classes:${JDEPS_TMP}/BOOT-INF/lib/*" \
    "${JDEPS_TMP}/BOOT-INF/classes" 2>/dev/null || true
)"
MANDATORY="java.desktop,java.logging,java.naming,java.management,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec"
ALL_MODULES="$(
  { echo "$JDEPS_MODULES"; echo "$MANDATORY"; } \
    | tr ',' '\n' | sed '/^$/d' | sort -u | paste -sd, -
)"
echo "    modules: ${ALL_MODULES}"

echo "==> Building custom runtime (jlink)"
jlink \
  --output "$RUNTIME" \
  --add-modules "$ALL_MODULES" \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=2

mkdir -p "${ROOT}/dist"
DMG_NAME="Crewcast-${FILE_VERSION}-macos-${ARCH_LABEL}.dmg"
DMG_PATH="${ROOT}/dist/${DMG_NAME}"

echo "==> Packaging .dmg (jpackage)"
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

# jpackage names the dmg after --name; rename to our versioned filename.
DEFAULT_DMG="${ROOT}/dist/Crewcast-${APP_VERSION}.dmg"
if [[ -f "$DEFAULT_DMG" && "$DEFAULT_DMG" != "$DMG_PATH" ]]; then
  mv -f "$DEFAULT_DMG" "$DMG_PATH"
elif [[ -f "${ROOT}/dist/Crewcast-1.0.0.dmg" && ! -f "$DMG_PATH" ]]; then
  mv -f "${ROOT}/dist/Crewcast-1.0.0.dmg" "$DMG_PATH"
fi

[[ -f "$DMG_PATH" ]] || die "Expected dmg not found at $DMG_PATH (check dist/)"

BYTES="$(stat -f%z "$DMG_PATH" 2>/dev/null || stat -c%s "$DMG_PATH")"
MB="$(awk "BEGIN {printf \"%.1f\", $BYTES / 1048576}")"

echo ""
echo "Done."
echo "  Output: ${DMG_PATH}"
echo "  Size:   ${MB} MiB (${BYTES} bytes)"
echo "  Test:   open \"${DMG_PATH}\" && drag Crewcast.app to Applications, then launch"
echo "          (unsigned: right-click → Open, or System Settings → Privacy & Security → Open Anyway)"
