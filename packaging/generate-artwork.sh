#!/usr/bin/env bash
# Cross-platform brand icons for installers (PNG + ICO).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "${ROOT}/packaging/tools/generate_icons.py"
