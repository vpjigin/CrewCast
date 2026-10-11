# Crewcast

Share videos from your computer to any device, on your Wi‑Fi or over the internet, with no upload, no account and no cloud storage.

![Crewcast](docs/images/screenshot.png)

**Crewcast** (by Solocrew) is a self-hosted Spring Boot app with a dark-themed desktop UI and a touch-friendly web player. Your files stay on your machine; viewers stream them while sharing is on.

---

## Features

- **Public link** — HTTPS via a Cloudflare Quick Tunnel (automatic in the desktop app), plus a **Wi‑Fi link**, **QR code**, and copy buttons
- **Folder and file sharing** — add libraries or individual videos; rescans on each request (no restart when files change on disk)
- **Viewer list** — see who is watching (in memory only, not saved to disk)
- **Stay awake** — optional keep-awake while sharing is on (desktop)
- **Audio fix** — remux/transcode for browser playback (MKV, E‑AC3, AC3, DTS, etc.) with a per-video **language / track picker**
- **Subtitles** — embedded and sidecar text tracks as WebVTT in the player
- **Chapter thumbnails** — scrubbing grid (default 20 tiles) to jump through long videos
- **Access key** — a private `?t=` token per sharing session; **New link** rotates it
- **No accounts, no analytics** — nothing phones home to Solocrew

Supported video extensions: `.mp4`, `.mkv`, `.mov`, `.webm`, `.m4v`.

---

## Requirements

- **JDK 21+** and **Maven** to build and run from source
- **Installers** (macOS / Windows / Linux) are planned; until then, use the steps below

---

## Run from source

```bash
git clone https://github.com/vpjigin/CrewCast.git
cd CrewCast
mvn -pl desktop -am install -DskipTests
mvn spring-boot:run -pl desktop
```

The embedded server binds to **`0.0.0.0` on a random port** (`server.port=0`). The desktop app shows the **Wi‑Fi** and **public** links (and QR) once sharing starts — there is no fixed port like 8080.

Add folders or files in the UI, configure playback options in the left panel, then click **Start sharing**.

---

## How sharing works

| Link | What it is |
|------|------------|
| **Public** | `https://….trycloudflare.com/?t=…` — HTTPS through Cloudflare’s Quick Tunnel to your computer |
| **Wi‑Fi** | `http://<your-LAN-IP>:<port>/?t=…` — direct on the local network |

The **`t` query parameter** is the access key for this sharing session. Anyone with the **full public link** (including `t`) can open the library and watch **while sharing is on**. Treat it like a password.

- **New link** (desktop) — generates a new access key; old links stop working
- **Stop sharing** — ends the session; links return **503 Sharing is off** until you start again

By default, **Require private key** is on. On the same Wi‑Fi, the app can allow LAN clients without the key (Settings); tunnel visitors always need the key when it is required.

Cloudflared is downloaded once into `~/.crewcast/bin/` if it is not already on your `PATH` (see [PRIVACY.md](PRIVACY.md)).

---

## Headless mode

Build the executable server JAR:

```bash
mvn -pl server -am package -DskipTests
java -jar server/target/crewcast-server-0.0.1-SNAPSHOT-exec.jar \
  --share /path/to/folder-or-file \
  [--share /another/path ...] \
  [--port N] \
  [--no-tunnel] \
  [--download-ffmpeg]
```

| Flag | Purpose |
|------|---------|
| `--share` | Folder or file to share (repeatable). Required. |
| `--port` | Listen port (default **random**, same as desktop). Use `0` for random. |
| `--no-tunnel` | Do not start the Cloudflare tunnel; LAN access only. |
| `--download-ffmpeg` | Download and install ffmpeg/ffprobe under `~/.crewcast/bin/` (non-interactive consent for headless). |

Headless mode prints **LAN** and **Public** URLs when ready. Press **Ctrl+C** to stop (tunnel and server shut down cleanly).

---

## Audio fix and ffmpeg

When **Fix audio for browsers** is enabled, Crewcast converts non-browser audio (AC3, E‑AC3, DTS, etc.) and non‑MP4 containers into a cached **MP4** with **AAC stereo**, video copied through (HEVC gets an `hvc1` tag where needed). Cover-art / attached-picture streams are ignored.

### ffmpeg availability

- **Lookup order:** configured path → `~/.crewcast/bin/` → `PATH` / common install locations
- **Desktop — proactive prompt:** If ffmpeg is missing, the app may show **Enable audio support?** when you **Start sharing** (once per run) or when you add a folder/file that includes non‑`.mp4`/`.m4v`/`.mov` video (e.g. `.mkv`). **Download**, **Choose existing…**, or **Not now** (turns the toggle off until you enable it again). See [docs/FFMPEG-SOURCES.md](docs/FFMPEG-SOURCES.md) for allow-listed download hosts and licenses.
- **Settings → Download ffmpeg** — same verified download flow anytime
- **Headless:** `--download-ffmpeg` before sharing
- **Manual install:** `brew install ffmpeg`, `winget install ffmpeg`, `apt install ffmpeg`, etc., then **Re-check ffmpeg** in Settings

**Tests:** `mvn -pl server test` does **not** download ffmpeg. Optional real-download test:

```bash
mvn -pl server test -Dcrewcast.ffmpeg.e2e=true
```

### Cache and playback

- **Cache:** `~/.crewcast/cache/` (or `$crewcast.home/cache`). Default cap **20 GB** with LRU eviction. **Clear audio cache** in Settings removes converted files.
- **Preferred language** — Settings chooses a default track (e.g. tagged Hindi, Malayalam); the player **Audio** control overrides per video (`localStorage`: `crewcast-audio-{id}`).
- **First play** of a track that is not ready yet — **503** on the stream plus a **preparing audio** overlay with progress; poll `/api/videos/{id}/audio-status`.
- **HEVC** — conversion keeps HEVC video but some browsers still struggle; the player may show a short notice.
- Remuxed audio cache uses `-sn`; captions are served separately via WebVTT URLs.

---

## Subtitles

The web player **Captions** menu (keyboard **c**) shows **Off** plus every **supported** text track. Choices are remembered per video in `localStorage` (`crewcast-captions-{id}`).

- **Embedded** — SubRip, ASS/SSA, `mov_text`, and WebVTT streams are listed in `GET /api/videos/{id}` and converted to WebVTT on first request (cached under `~/.crewcast/cache/subtitles/`, separate from the audio remux cache).
- **Sidecar** — In the same folder as `film.mkv`, Crewcast also looks for `film.srt`, `film.vtt`, `film.en.srt`, `film.ml.srt`, and similar two- or three-letter language suffixes. Sidecars are transcoded to UTF-8 WebVTT (SRT timestamps normalized, charset detected from BOM / heuristics).
- **Bitmap subtitles** (PGS, DVD, DVB, etc.) appear in the API with `supported: false` but are not served yet.
- **Settings → Default captions** — Off (default), match **preferred audio language**, or **English** when the viewer has not picked captions before.

---

## Player usage

Dark-themed full-screen player at `/watch/{id}`:

- **Tap the video** (or click) to play/pause; center icon feedback
- **Bottom bar** — seek slider, time, **−10s**, play/pause, **+10s**, mute
- **Top bar** — **Audio** track (when multiple tracks exist), **Captions**, playback **speed** (0.5×–2×), **Chapters** grid, **fullscreen**
- **Audio** — dropdown for quick switches; first-time or heavy tracks may open a **Choose audio language** panel with readiness hints
- **Chapters** — grid of thumbnails across the timeline (count from Settings, default 20); first open may take a few seconds while frames are captured
- Controls auto-hide while playing; tap or move the pointer to show them again

Home page `/` lists shared videos (requires a valid access key when sharing is on and the key is required).

---

## Privacy

Crewcast contacts the network only for:

| Purpose | Destination |
|---------|-------------|
| Public tunnel | Cloudflare (Quick Tunnel) |
| `cloudflared` binary | GitHub API + GitHub releases (`cloudflare/cloudflared`) on first tunnel use if needed |
| Optional **ffmpeg** download | Allow-listed hosts in [docs/FFMPEG-SOURCES.md](docs/FFMPEG-SOURCES.md), **only after you consent** (desktop) or `--download-ffmpeg` (headless) |

There is **no** Solocrew account, analytics SDK, or third-party CDN in the web player.

- Active **viewers** — kept in memory (~30 seconds) for the desktop UI; not written to disk
- Access key — generated when sharing starts; **not** stored in `settings.json`

More detail: [PRIVACY.md](PRIVACY.md), [TERMS.md](TERMS.md), [docs/PRIVACY-INVENTORY.md](docs/PRIVACY-INVENTORY.md).

---

## Known limitations

- **Quick tunnels** — Cloudflare Quick Tunnel URLs are best-effort; no uptime SLA. Use **Wi‑Fi** or `--no-tunnel` if you need LAN-only.
- **Disk use** — Each prepared **audio language** is a separate converted MP4 in cache (up to the **20 GB** cap); multi-track movies can use significant space.
- **HEVC (H.265)** — May not play in every browser even after remux; Safari often fares better than Chrome on some files.
- **Bitmap subtitles** — Detected but not converted; use text-based tracks or sidecars.
- **Builds** — Source builds are **unsigned**; verify and build yourself if that matters for your environment.
- **Chapters** — First open per video seeks through the file to build thumbnails; cached for the session afterward.

---

## Project structure

```
CrewCast/
├── pom.xml                    # parent — Java 21, Spring Boot 3.3.x
├── server/                    # crewcast-server (library + headless exec JAR)
│   └── src/main/java/com/solocrew/crewcast/
│       ├── CrewcastApplication.java, ServerApp.java
│       ├── VideoController.java, SubtitleController.java
│       ├── facade/            # SharingService, UI listeners
│       ├── share/, access/, web/, viewers/, tunnel/, system/, settings/, media/
│       └── resources/static/  # index.html, player/
└── desktop/                   # crewcast-desktop — Swing UI (DesktopApp)
    └── src/main/java/com/solocrew/crewcast/desktop/
```

Data directory: `~/.crewcast/` (settings, cache, optional `bin/` for cloudflared and ffmpeg). Legacy `~/.lanstream` is migrated on first run.

---

## Key endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /` | Home page (video list) |
| `GET /watch/{id}` | Player page |
| `GET /stream/{id}` | Range-request video stream (`?audio=N`, `?t=` when required) |
| `GET /api/videos` | JSON list `{ id, title }` |
| `GET /api/videos/{id}` | Metadata (audio tracks, subtitles, chapters flag, progress) |
| `GET /api/videos/{id}/audio-status` | Audio preparation progress |
| `POST /api/videos/{id}/audio/prepare` | Start preparing a track |
| `GET /subtitles/{id}/{index}.vtt` | WebVTT captions (UTF-8) |

When sharing is off, protected routes respond with **503**. When **Require private key** is on, missing or wrong `t` yields **403** (except configured LAN bypass).

---

## Contributing

Bug reports and ideas are welcome via [GitHub Issues](https://github.com/vpjigin/CrewCast/issues). There is no PR template yet; small focused changes are easiest to review.

---

## Contact

- **Bugs and feature requests** — [GitHub Issues](https://github.com/vpjigin/CrewCast/issues) on [github.com/vpjigin/CrewCast](https://github.com/vpjigin/CrewCast)
- **Security problems** — Use the repository’s **Report a vulnerability** button (Security tab), or email [solocrew.me@gmail.com](mailto:solocrew.me@gmail.com). Do not post vulnerabilities in public issues.
- **General questions and business enquiries** — [solocrew.me@gmail.com](mailto:solocrew.me@gmail.com)

---

## License

Crewcast is released under the [MIT License](LICENSE). Third-party libraries and fonts are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

The names **Crewcast** and **Solocrew** are not licensed for use as product names by others; the source code is free to use, copy, modify, and distribute under MIT.
