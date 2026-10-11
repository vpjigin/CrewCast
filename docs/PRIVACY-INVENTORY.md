# Privacy inventory (Crewcast)

Technical inventory of what Crewcast stores, logs, and sends over the network.
For user-facing language, see `PRIVACY.md` (draft).

## Summary table

| What | Who / where | Why | When | Optional? |
|------|-------------|-----|------|-----------|
| Share paths & app settings (`settings.json`) | Disk: `~/.crewcast/settings.json` (mode **600** on Unix) | Remember folders/files, tunnel prefs, optional cloudflared path | On save / at exit | Required for persistent shares |
| Session access key (`?t=` / cookie) | **Memory only** (`AccessSession`) | Gate access while sharing | Regenerated each time sharing starts | Can disable “require private key” |
| Transcoded audio cache files | Disk: `~/.crewcast/cache/` | Faster replay of browser-compatible audio | When audio fix prepares tracks | Optional feature |
| cloudflared binary | Disk: `~/.crewcast/bin/cloudflared` | Public tunnel without separate install | First tunnel use if not on `PATH` / not configured | Optional (LAN-only / `--no-tunnel`) |
| Active viewer list | **Memory only** (`ViewerRegistry`) | Show “who’s watching” in desktop UI | Last **30 seconds** of activity per IP+UA key | Core to viewer panel while sharing |
| Blocked viewers set | **Memory only** | Enforce block until sharing restarts | Until sharing stopped/restarted | Optional (user blocks someone) |
| HTTP 403 / 503 logs | **Console / log file** (Logback) | Debug access denials | On denied requests | INFO: reason + path only; IP/UA at DEBUG |
| Video bytes & metadata | **Your Mac** (original files) | Streaming | On viewer request | N/A — you chose the files |
| Tunnel traffic | **Cloudflare** (Quick Tunnel) | Reach viewers on the internet | While tunnel running | Optional |

## What stays on your computer

- **Settings** — JSON under `~/.crewcast/settings.json` (share entries, preferences, optional `cloudflaredBinaryPath`). Legacy `accessToken` / similar keys in old files are **ignored on load** and **removed on next save**. File permissions are restricted to owner read/write on Unix.
- **Cache** — Transcoded MP4 fragments under `~/.crewcast/cache/` when audio fix is used.
- **Binaries** — Downloaded `cloudflared` under `~/.crewcast/bin/` when applicable (verified via GitHub release **digest** metadata over HTTPS).
- **Viewers** — In RAM only, ~30 s rolling window.
- **No analytics SDK**, crash reporter, or update phone-home in application code.

## What goes through Cloudflare when you use the public link

- Viewers connect to a `*.trycloudflare.com` URL; HTTP(S) requests and video stream bytes traverse Cloudflare’s Quick Tunnel to your local Spring Boot server.
- Cloudflare sees connection metadata typical of a reverse proxy (IPs, TLS, URLs, timing).

## Downloads on first use

| Download | Trigger | Endpoint | Verification |
|----------|---------|----------|--------------|
| cloudflared | Tunnel start, no PATH/custom binary | `api.github.com` → official GitHub release asset URL | HTTPS + SHA-256 from GitHub API `digest` field on the asset (no separate `.sha256` files in current releases) |
| ffmpeg | Not downloaded | User installs locally | N/A |

## What we do not collect

- No Solocrew-operated telemetry or cloud library index.
- No third-party analytics in the web player.
- No persistent viewer history on disk.
- No session access keys on disk.

## Logging

`AccessDenialLogger` at **INFO** logs only `reason` and `path` for 403/503. Client IP, User-Agent, and Cloudflare forwarding headers are logged at **DEBUG** only.
