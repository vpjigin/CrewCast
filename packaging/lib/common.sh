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

packaging_assert_numeric_version() {
  local label="$1"
  local v="$2"
  if [[ ! "$v" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]]; then
    local trunc="${v:0:80}"
    packaging_die "Invalid ${label} version '${trunc}' (expected numeric major[.minor[.patch]])."
  fi
}

packaging_maven_version() {
  local root="$1"
  local pom="${root}/pom.xml"
  [[ -f "$pom" ]] || packaging_die "Missing pom.xml: ${pom}"
  local v
  v="$(python3 - "${pom}" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

NS = "{http://maven.apache.org/POM/4.0.0}"
pom = ET.parse(sys.argv[1])
project = pom.getroot()

def text_version(node):
    if node is None:
        return None
    for tag in (NS + "version", "version"):
        el = node.find(tag)
        if el is not None and el.text and el.text.strip():
            return el.text.strip()
    return None

ver = text_version(project)
if not ver:
    parent = project.find(NS + "parent")
    if parent is None:
        parent = project.find("parent")
    ver = text_version(parent)
if not ver:
    raise SystemExit("no version element")
ver = re.sub(r"-SNAPSHOT$", "", ver)
if not re.fullmatch(r"[0-9]+(\.[0-9]+){0,2}", ver):
    print(ver[:80], file=sys.stderr)
    raise SystemExit(2)
print(ver)
PY
)" || packaging_die "Could not read project version from ${pom}"
  packaging_assert_numeric_version "Maven" "$v"
  echo "$v"
}

packaging_desktop_jar() {
  local root="$1"
  local dir="${root}/desktop/target"
  local -a matches=()
  local f
  shopt -s nullglob
  for f in "${dir}"/crewcast-desktop-*.jar; do
    case "$f" in
      *.original | *-sources.jar | *-javadoc.jar) continue ;;
    esac
    matches+=("$f")
  done
  shopt -u nullglob
  if (( ${#matches[@]} != 1 )); then
    local listed="(none)"
    if (( ${#matches[@]} > 0 )); then
      listed="${matches[*]}"
    fi
    packaging_die "Expected exactly one crewcast-desktop jar in ${dir}; found: ${listed}"
  fi
  echo "${matches[0]}"
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
