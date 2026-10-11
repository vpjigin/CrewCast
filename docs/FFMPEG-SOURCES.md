# FFmpeg / ffprobe static build sources (research)

Crewcast needs **ffmpeg** and **ffprobe** together for browser audio fix and subtitles. This document compares candidate third-party static builds (verified March 2026) and records what Crewcast uses.

## Summary table

| Source | Platforms | Format | Checksum | License (typical) | Reliability | Crewcast use |
|--------|-----------|--------|----------|-------------------|-------------|--------------|
| **BtbN/FFmpeg-Builds** (GitHub) | Windows x64, Linux x64, Linux arm64 | `.zip` (Win), `.tar.xz` (Linux) | **`checksums.sha256`** per release + GitHub asset `digest` | **LGPL** (`*-lgpl` variants) or GPL (`*-gpl`) | Daily CI builds; `latest` tag; 14-day retention | **Yes — Win/Linux** |
| **ffmpeg.martin-riedl.de** | macOS Intel (amd64), Apple Silicon (arm64) | `.zip` per binary | **`.sha256` sidecar** next to each zip | GPL-style static builds (x264/x265 etc.) | Stable redirect URLs; Cloudflare-hosted | **Yes — macOS** |
| **evermeet.cx** | macOS Intel only | `.7z` / `.zip` | **GPG** signatures, not SHA-256 | **GPL** (`--enable-gpl`) | Long-running; author **no native Apple Silicon** builds | No — no SHA-256, no arm64 |
| **osxexperts.net** | macOS Intel + ARM (separate binaries) | raw binary download | **SHA-256 on page** (manual, no API) | **GPL** (x264/x265 in build scripts) | Small site; checksums tied to page HTML | No — fragile automation |
| **johnvansickle.com** | Linux x64/arm64/etc. | `.tar.xz` | **MD5 only** (`.md5` files) | **GPL v3** | Infrequent updates; site availability concerns | No — MD5 not SHA-256; GPL |
| **org.bytedeco:ffmpeg** (Maven) | JVM classifiers per OS/CPU | JAR + native libs | Maven artifact hashes (not release checksum workflow) | **GPL** prebuilt | Heavy dependency; not plain CLI layout | No — not CLI-focused |

## BtbN/FFmpeg-Builds (Windows + Linux)

- **URL pattern:** `https://github.com/BtbN/FFmpeg-Builds/releases/download/<tag>/<asset-name>`
- **Latest API:** `https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/latest`
- **Assets used:** `ffmpeg-master-latest-win64-lgpl.zip`, `ffmpeg-master-latest-linux64-lgpl.tar.xz`, `ffmpeg-master-latest-linuxarm64-lgpl.tar.xz` (static, includes `bin/ffmpeg` and `bin/ffprobe`).
- **Checksum:** each release ships `checksums.sha256`; GitHub release assets also expose `digest: sha256:…` when available.
- **License:** LGPL builds omit GPL-only libs (e.g. libx264/libx265). Crewcast picks **`-lgpl`** variants to reduce copyleft surface vs full GPL builds.
- **Reliability:** Automated daily builds; explicit retention policy; widely used in open-source tooling.

## ffmpeg.martin-riedl.de (macOS)

- **URL pattern:** `https://ffmpeg.martin-riedl.de/redirect/latest/macos/{amd64,arm64}/snapshot/{ffmpeg,ffprobe}.zip` → `https://ffmpeg.martin-riedl.de/download/macos/…/*.zip`
- **Checksum:** `ffmpeg.zip.sha256` and `ffprobe.zip.sha256` beside each build (format: `<hex>  filename`).
- **License:** Static builds with GPL components (similar to Homebrew/ffmpeg.org “full” builds). **Downloaded on user consent, not bundled** in Crewcast MIT source.
- **Reliability:** Redirect API for automation; separate Intel and Apple Silicon artifacts.

## evermeet.cx

- Intel macOS only; **GPG**-signed packages; **no published SHA-256** for programmatic verify.
- Author states **no plans for Apple Silicon** static binaries.
- **Not suitable** for Crewcast’s fail-closed SHA-256 requirement and arm64 Mac support.

## osxexperts.net

- Publishes **SHA-256** on the HTML page for Intel and ARM ffmpeg/ffprobe.
- No stable machine-readable checksum API; GPL-oriented build recipes.
- **Not chosen** (operational fragility vs Martin Riedl’s sidecar files + redirects).

## johnvansickle.com

- Linux-focused static builds with **MD5** sidecars only; **GPL v3** explicitly stated in readme.
- **Not chosen** (SHA-256 requirement; overlap with BtbN for Linux).

## org.bytedeco (Maven Central)

- Java-native packaging; **GPL** prebuilds; large transitive footprint.
- **Not chosen** — Crewcast invokes CLI binaries like cloudflared, not JNI.

## Crewcast choice

| OS / CPU | Host | Variant |
|----------|------|---------|
| Windows x64 | GitHub — **BtbN/FFmpeg-Builds** | `win64-lgpl` zip |
| Linux x64 | GitHub — **BtbN/FFmpeg-Builds** | `linux64-lgpl` tar.xz |
| Linux arm64 | GitHub — **BtbN/FFmpeg-Builds** | `linuxarm64-lgpl` tar.xz |
| macOS amd64 | **ffmpeg.martin-riedl.de** | snapshot `ffmpeg.zip` + `ffprobe.zip` |
| macOS arm64 | **ffmpeg.martin-riedl.de** | snapshot `ffmpeg.zip` + `ffprobe.zip` |

**Why:** Only combination that covers **all five** targets with **HTTPS**, **allow-listed hosts**, and **SHA-256 verification** (fail closed). BtbN provides **LGPL** builds on GitHub for Win/Linux; Martin Riedl fills the **macOS gap** BtbN does not ship, with per-file `.sha256` sidecars.

## Per-platform license (chosen builds)

Crewcast **does not bundle or redistribute** these binaries. They are downloaded **only at the user’s request** (desktop consent, Settings, or `--download-ffmpeg`) into `~/.crewcast/bin/`.

| Platform | Build | License (as configured) | How we know |
|----------|--------|-------------------------|-------------|
| Windows x64 | BtbN `*-win64-lgpl.zip` | **LGPL v2.1+** (no libx264/libx265 in `-lgpl` builds) | BtbN variant docs; verify with downloaded `ffmpeg -version` / `ffmpeg -L` |
| Linux x64 | BtbN `*-linux64-lgpl.tar.xz` | **LGPL v2.1+** | Same |
| Linux arm64 | BtbN `*-linuxarm64-lgpl.tar.xz` | **LGPL v2.1+** | Same |
| macOS Intel / Apple Silicon | ffmpeg.martin-riedl.de snapshot zips | **GPL v2 or later** (build uses `--enable-gpl`, libx264, libx265, etc.) | Verified on **macOS arm64** download (Oct 2026): `ffmpeg -version` reports GPL; configuration includes `--enable-gpl`, `--enable-libx264`, `--enable-libx265`; `ffmpeg -L` lists `libx264` / `libx265` under GPL |

### Trust note (macOS)

The macOS binaries are served from **ffmpeg.martin-riedl.de**, a static-build site operated by an **individual maintainer** (not FFmpeg project official infrastructure). Crewcast downloads over HTTPS, verifies **SHA-256 checksums published on the same host** (`.sha256` sidecar files next to each zip), and runs `ffmpeg -version` before use. If you prefer not to trust that host, use **Choose existing…** in the dialog or install ffmpeg via Homebrew and skip the download.

### Archive extraction (no new Java dependencies)

| Format | Extraction |
|--------|------------|
| BtbN `.tar.xz` (Linux) | OS **`tar -xJf`** via `ProcessBuilder` — requires **GNU/BSD tar with xz** (standard on Linux/macOS; Windows 10+ includes tar) |
| BtbN `.zip` (Windows) | Java **`ZipInputStream`** (JDK) |
| macOS `.zip` | Java **`ZipInputStream`** (JDK) |

No additional Maven libraries were added for decompression.

## License implications for Crewcast (MIT app)

- Crewcast **source** remains MIT. **ffmpeg/ffprobe binaries are not shipped** in the repo; the user (or `--download-ffmpeg`) downloads them after consent.
- **LGPL (BtbN `-lgpl`):** If you **redistribute** those binaries, LGPL terms apply to the binaries and require corresponding source/License compliance for **that redistribution**. Using them locally on your own machine for transcoding is the intended case.
- **GPL (Martin Riedl macOS builds):** Same pattern — **local use** after download; **redistributing** the downloaded binaries triggers GPL obligations for the binaries.
- Crewcast does **not** link against ffmpeg; it **spawns processes**, similar to cloudflared.

See also [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) and [PRIVACY.md](../PRIVACY.md).
