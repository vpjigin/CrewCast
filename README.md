# Crewcast

**Crewcast** (by Solocrew) is a self-hosted Spring Boot app that streams video files from your Mac to any
device on the same Wi-Fi — or over the internet via a tunnel — with a custom
touch-friendly player (seek, speed control, mute, fullscreen) and a 20-tile
scrubbing thumbnail grid ("Chapters") for quick navigation through long videos.

---

## 1. Shared videos

Supported formats: `.mp4`, `.mkv`, `.mov`, `.webm`, `.m4v`.

In the **desktop app**, add folders or individual files with **Folder** /
**Files** (or drag-and-drop). Shares are saved in `~/.crewcast/settings.json`.

For **headless** mode, pass `--share /path/to/folder-or-file` (see section 3).

The server rescans shared folders on every request — no restart needed when
files change on disk.

---

## 2. Requirements

- **Recommended runtime: JDK 21 LTS** (works on Java 21+)
- Maven (`brew install maven` if you don't have it)

---

## 3. Running the app

**Desktop app (recommended):**

```bash
cd crewcast
mvn -pl desktop -am install -DskipTests
mvn spring-boot:run -pl desktop
```

Add folders or files in the UI, then click **Start sharing**.

**Headless server (no UI):**

```bash
mvn -pl server -am package -DskipTests
java -jar server/target/crewcast-server-0.0.1-SNAPSHOT-exec.jar --share /path/to/videos
```

Optional flags: `--share` (repeatable), `--port N` (default random), `--no-tunnel`.
Press Ctrl+C to stop (cloudflared is torn down cleanly).

If you ever hit an `OutOfMemoryError` on very large files, you can raise the
heap as a temporary workaround:
```bash
mvn spring-boot:run -pl desktop -Dspring-boot.run.jvmArguments="-Xmx2048m"
```
(The streaming endpoint uses Spring's `ResourceRegion` support to stream
directly from disk rather than buffering full files in memory, so this
shouldn't normally be needed — but it's a safe stopgap if it comes up again.)

Once running, you'll see `Started CrewcastApplication` in the logs.

---

## 4. Watching on the same Mac

Open in a browser:
```
http://localhost:8080/
```
You'll see a list of videos in your folder — click one to open the player.

---

## 5. Watching from another device on the same Wi-Fi

1. Find your Mac's LAN IP:
   ```bash
   ipconfig getifaddr en0
   ```
   (If empty, try `en1`. Or check System Settings → Wi-Fi → ⓘ next to your
   network.)

2. On the other device (same Wi-Fi network), open:
   ```
   http://<that-ip>:8080/
   ```

**If it doesn't load:**
- Check macOS Firewall (System Settings → Network → Firewall) isn't blocking
  incoming connections for Java. Find your real Java binary with `which java`
  and allow it explicitly if needed — the generic `/usr/bin/java` path is
  usually just a stub, not the real JVM.
- Confirm both devices are on the **same SSID**, not a guest/isolated network
  (guest networks often block device-to-device traffic).
- Confirm the app is listening on all interfaces, not just localhost:
  ```bash
  lsof -iTCP -sTCP:LISTEN -n -P | grep 8080
  ```
  You want to see `*:8080`, not `127.0.0.1:8080`.

---

## 6. Watching from outside your Wi-Fi (internet access via tunnel)

If you want to share a video with someone not on your Wi-Fi, use a tunnel to
get a public URL pointing at your Mac's `localhost:8080`.

**Recommended: Cloudflare Tunnel** (free, no bandwidth cap):
```bash
brew install cloudflared
cloudflared tunnel --url http://localhost:8080
```
This prints a public `https://xxxx.trycloudflare.com` URL you can share.
Keep the terminal window open — closing it ends the tunnel.

If you see `stream canceled by remote` errors in the cloudflared logs while
using the Chapters grid, that's expected — the thumbnail feature seeks
rapidly through 20 timestamps, and each new request cancels the previous
in-flight one. It's cosmetic log noise, not a broken stream, as long as
playback itself works.

**If the connection feels unstable** (frequent drops, playback stalling
beyond the expected Chapters noise above), try forcing HTTP/2 instead of the
default QUIC transport:
```bash
cloudflared tunnel --url http://localhost:8080 --protocol http2
```
QUIC (the default) runs over UDP and is usually faster, but some
routers/ISPs handle UDP inconsistently, which can show up as random
disconnects. HTTP/2 runs over plain TCP — slightly more overhead, but more
predictable on networks that don't play nicely with UDP.

> ⚠️ Anyone with the tunnel URL can access your videos while the tunnel is
> running — there's no authentication built in. Only share the link with
> people you trust, and stop the tunnel (`Ctrl+C`) when you're done.

---

## 7. Using the player

- **Tap the video** to play/pause
- **Bottom bar**: seek slider with current/total time, −10s, play/pause,
  +10s, mute
- **Top-right**: playback speed (0.5x–2x), fullscreen toggle, and
  **Chapters** — opens a grid of 20 thumbnails spaced evenly across the
  video's duration; tap any tile to jump straight to that point
- Controls auto-hide after a few seconds while playing, and reappear on tap
  or mouse movement

---

## 8. Keeping the Mac awake while streaming

If your Mac sleeps, the server stops responding to other devices. To keep it
awake for the duration of a streaming session, run this in a separate
terminal tab while the app is running:
```bash
caffeinate -d
```

---

## 9. Project structure

```
crewcast/
├── pom.xml                          # parent (Java 21, Spring Boot 3.2.5)
├── server/                          # crewcast-server — Spring Boot library + exec jar
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/solocrew/crewcast/
│       │   ├── CrewcastApplication.java
│       │   ├── ServerApp.java       # headless CLI entry
│       │   ├── VideoController.java # stream + JSON API (no HTML)
│       │   ├── facade/SharingService.java
│       │   ├── share/, access/, web/, viewers/, tunnel/, system/, settings/
│       └── resources/
│           ├── application.properties
│           └── static/
│               ├── index.html, index.js
│               └── player/watch.html, player.css, player.js
└── desktop/                         # crewcast-desktop — Swing UI
    ├── pom.xml
    └── src/main/java/com/solocrew/crewcast/
        ├── DesktopApp.java
        └── desktop/MainFrame.java, SettingsDialog.java
```

Key endpoints:
| Endpoint | Purpose |
|---|---|
| `GET /` | Home page (static list of videos) |
| `GET /watch/{id}` | Custom player page |
| `GET /stream/{id}` | Range-request video stream (used by the player) |
| `GET /api/videos` | JSON list of `{ id, title }` |
| `GET /api/videos/{id}` | Metadata for the player (audio tracks, subtitles, chapters flag) |
| `GET /subtitles/{id}/{index}.vtt` | WebVTT caption file (UTF-8) for a supported track |

---

## 10. Subtitles

The web player **Captions** menu (keyboard **c**) shows **Off** plus every **supported**
text track. Choices are remembered per video in `localStorage` (`crewcast-captions-{id}`).

- **Embedded** — SubRip, ASS/SSA, `mov_text`, and WebVTT streams are listed in
  `GET /api/videos/{id}` and converted to WebVTT on first request (cached under
  `~/.crewcast/cache/subtitles/`, separate from the audio remux cache).
- **Sidecar** — In the same folder as `film.mkv`, Crewcast also looks for `film.srt`,
  `film.vtt`, `film.en.srt`, `film.ml.srt`, and similar two- or three-letter language
  suffixes. Sidecars are transcoded to UTF-8 WebVTT (SRT timestamps normalized, charset
  detected from BOM / heuristics).
- **Bitmap subtitles** (PGS, DVD, DVB, etc.) appear in the API with `supported: false` but
  are not served yet.
- **Settings → Default captions** — Off (default), match preferred audio language, or English
  when the viewer has not picked captions before.

---

## 11. Audio fix (ffmpeg)

When **Fix audio for browsers** is enabled (Settings or the left-panel toggle), Crewcast
converts non-browser audio (AC3, E-AC3, DTS, etc.) and non-MP4 containers into a cached
**MP4** with **AAC stereo** video copied through (HEVC gets an `hvc1` tag for Safari/Chrome).
Cover-art / attached-picture streams are ignored; the main video stream is mapped by its
real index (e.g. `-map 0:0`), and audio uses audio-relative indices (`-map 0:a:N`, matching
`?audio=N` in the player).

- **ffmpeg** — Required for audio fix and subtitles. Lookup order: configured path →
  `~/.crewcast/bin/` → `PATH` / Homebrew. In the **desktop app**, use **Download ffmpeg**
  in Settings (one-time, SHA-256 verified; see [docs/FFMPEG-SOURCES.md](docs/FFMPEG-SOURCES.md)).
  Headless: `ServerApp --download-ffmpeg …`. Manual install: `brew install ffmpeg`,
  `winget install ffmpeg`, or `apt install ffmpeg`. **Re-check ffmpeg** after installing.

**Tests:** `mvn -pl server test` does **not** download ffmpeg. To run the optional
real-download integration test (~50–180 MB, network):

```bash
mvn -pl server test -Dcrewcast.ffmpeg.e2e=true
```
- **Cache** — `~/.crewcast/cache/` (or `$crewcast.home/cache` when using
  `-Dcrewcast.home=…`). Default size cap **20 GB** with LRU eviction. **Clear audio cache**
  in Settings removes converted files.
- **Preferred language** — Settings → **Auto** uses track 0; otherwise picks Hindi,
  Malayalam, etc. when tagged. The player **Audio** menu overrides per video (saved in
  `localStorage`).
- **H.265 / HEVC** — Browsers may still struggle with HEVC in MP4; the player shows a short
  notice. Conversion keeps HEVC video but fixes audio.
- **Limitations** — Remuxed MP4 cache still drops subtitles (`-sn`); captions use separate
  WebVTT URLs. The **first play** of a non-default audio track waits for that track’s
  conversion (503 + progress overlay).

---

## 12. Known limitations

- No authentication — anyone with network/tunnel access can browse and watch
  your videos. Fine for home/trusted-sharing use; add Spring Security if this
  ever needs to be more locked down.
- Chapters thumbnail generation takes a few seconds the first time you open
  it for a given video (it seeks through the file 20 times to capture
  frames); it's cached in memory afterward for that session.
- `.mkv` seek behavior can vary slightly by browser depending on the codec
  inside the container.

---

## 13. License

Crewcast is released under the [MIT License](LICENSE). Third-party libraries and fonts
are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

The names **Crewcast** and **Solocrew** are not licensed for use as product names by
others; the source code is free to use, copy, modify, and distribute under MIT.