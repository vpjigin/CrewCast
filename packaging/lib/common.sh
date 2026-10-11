#!/usr/bin/env bash
# Shared helpers for packaging/build-*.sh (source, do not execute).
packaging_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd
}

packaging_die() {
  echo "ERROR: $*" >&2
  exit 1
}

packaging_require_cmd() {
  command -v "$1" >/dev/null 2>&1 || packaging_die "Missing required command: $1"
}

packaging_maven_version() {
  local root="$1"
  (cd "$root" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version) | sed 's/-SNAPSHOT$//'
}

# jpackage-safe numeric version: CREWCAST_RELEASE_VERSION if valid, else Maven (with warning).
packaging_resolve_version() {
  local root="$1"
  local maven_fallback
  maven_fallback="$(packaging_maven_version "$root")"

  if [[ -n "${CREWCAST_RELEASE_VERSION:-}" ]]; then
    local raw="${CREWCAST_RELEASE_VERSION#v}"
    raw="${raw%%-*}"
    if [[ "$raw" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]]; then
      echo "$raw"
      return
    fi
    echo "WARNING: CREWCAST_RELEASE_VERSION='${CREWCAST_RELEASE_VERSION}' is not a valid numeric version; using Maven fallback ${maven_fallback}." >&2
  else
    echo "WARNING: No valid CREWCAST_RELEASE_VERSION; using Maven fallback ${maven_fallback}." >&2
  fi
  echo "$maven_fallback"
}

# macOS jpackage: major version must be >= 1.
packaging_app_version_macos() {
  local v="$1"
  local major="${v%%.*}"
  if [[ "$major" =~ ^[0-9]+$ ]] && (( major < 1 )); then
    echo "1.0.0"
  else
    echo "$v"
  fi
}

# Windows MSI / Linux deb: three numeric components.
packaging_app_version_triple() {
  local v="$1"
  local major="${v%%.*}"
  if [[ "$major" =~ ^[0-9]+$ ]] && (( major < 1 )); then
    echo "1.0.0"
    return
  fi
  if [[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+ ]]; then
    echo "$v"
  elif [[ "$v" =~ ^[0-9]+\.[0-9]+$ ]]; then
    echo "${v}.0"
  elif [[ "$v" =~ ^[0-9]+$ ]]; then
    echo "${v}.0.0"
  else
    echo "1.0.0"
  fi
}

packaging_maven_build() {
  local root="$1"
  (cd "$root" && mvn -pl desktop -am package -DskipTests)
}

packaging_desktop_jar() {
  local root="$1"
  local mvn_version
  mvn_version="$(cd "$root" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
  echo "${root}/desktop/target/crewcast-desktop-${mvn_version}.jar"
}

packaging_discover_modules() {
  local jar="$1"
  local tmp
  tmp="$(mktemp -d)"
  ( cd "$tmp" && jar xf "$jar" )
  local jdeps_modules
  jdeps_modules="$(
    jdeps --ignore-missing-deps --multi-release 21 --recursive --print-module-deps \
      --class-path "${tmp}/BOOT-INF/classes:${tmp}/BOOT-INF/lib/*" \
      "${tmp}/BOOT-INF/classes" 2>/dev/null || true
  )"
  rm -rf "$tmp"
  local mandatory="java.desktop,java.logging,java.naming,java.management,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec"
  { echo "$jdeps_modules"; echo "$mandatory"; } \
    | tr ',' '\n' | sed '/^$/d' | sort -u | paste -sd, -
}

packaging_jlink_runtime() {
  local modules="$1"
  local runtime_dir="$2"
  rm -rf "$runtime_dir"
  jlink \
    --output "$runtime_dir" \
    --add-modules "$modules" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2
}

packaging_ensure_java_21() {
  local major
  major="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  if [[ -z "${major}" ]] || (( major < 21 )); then
    packaging_die "JDK 21+ required (found Java ${major:-unknown})."
  fi
}
