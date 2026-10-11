# Third-party notices

Crewcast is distributed under the **MIT License** (see [LICENSE](LICENSE)). The
components below were reviewed for compatibility with distributing this project under MIT.
**No runtime dependency is flagged as incompatible** with MIT distribution when used as
linked libraries (no copyleft source-distribution requirement for Crewcast itself).

> Versions match the root `pom.xml` / Spring Boot BOM at build time (`3.3.13`).

## Maven dependencies (runtime — desktop app)

| Component | Version | License | Source |
|-----------|---------|---------|--------|
| Spring Boot (`spring-boot-starter-web` and transitive stack) | 3.3.13 | Apache-2.0 | https://spring.io/projects/spring-boot |
| Spring Framework (`spring-web`, `spring-webmvc`, etc.) | 6.1.21 | Apache-2.0 | https://spring.io/projects/spring-framework |
| Apache Tomcat Embed | 10.1.42 | Apache-2.0 | https://tomcat.apache.org/ |
| Jackson (`jackson-databind`, modules) | 2.17.3 | Apache-2.0 | https://github.com/FasterXML/jackson |
| Logback | 1.5.18 | EPL-1.0 **or** LGPL-2.1 (dual) | https://logback.qos.ch/ |
| SLF4J | 2.0.17 | MIT | https://www.slf4j.org/ |
| Apache Log4j API (via `log4j-to-slf4j`) | 2.23.1 | Apache-2.0 | https://logging.apache.org/log4j/ |
| SnakeYAML | 2.2 | Apache-2.0 | https://bitbucket.org/snakeyaml/snakeyaml |
| Micrometer (observation) | 1.13.15 | Apache-2.0 | https://micrometer.io/ |
| FlatLaf | 3.4.1 | Apache-2.0 | https://github.com/JFormDesigner/FlatLaf |
| FlatLaf Extras | 3.4.1 | Apache-2.0 | https://github.com/JFormDesigner/FlatLaf |
| JSVG (FlatLaf Extras runtime) | 1.4.0 | MIT | https://github.com/weisJ/jsvg |
| MigLayout Swing / Core | 11.4 | BSD-3-Clause | https://www.miglayout.com/ |
| ZXing Core / JavaSE | 3.5.3 | Apache-2.0 | https://github.com/zxing/zxing |
| JCommander (ZXing JavaSE) | 1.82 | Apache-2.0 | https://jcommander.org/ |
| JAI ImageIO Core (ZXing JavaSE runtime) | 1.4.0 | BSD-3-Clause | https://github.com/jai-imageio/jai-imageio-core |
| Apache Commons Compress | 1.27.1 | Apache-2.0 | https://commons.apache.org/proper/commons-compress/ |
| Apache Commons IO (Commons Compress runtime) | 2.16.1 | Apache-2.0 | https://commons.apache.org/proper/commons-io/ |
| Apache Commons Lang3 (Commons Compress runtime) | 3.14.0 | Apache-2.0 | https://commons.apache.org/proper/commons-lang/ |
| Apache Commons Codec (Commons Compress runtime) | 1.16.1 | Apache-2.0 | https://commons.apache.org/proper/commons-codec/ |
| XZ for Java (Commons Compress `.tar.xz`) | 1.10 | Public Domain | https://tukaani.org/xz/java.html |

## Maven dependencies (test only — not shipped in end-user jar unless you bundle tests)

| Component | Version | License | Source |
|-----------|---------|---------|--------|
| Spring Boot Test | 3.3.13 | Apache-2.0 | https://spring.io/projects/spring-boot |
| Microsoft Playwright | 1.49.0 | Apache-2.0 | https://playwright.dev/java/ |

## Fonts (bundled in desktop JAR)

| Font | Version / files | License | Source |
|------|-----------------|---------|--------|
| Manrope (Medium, SemiBold, Bold, ExtraBold) | TTF files in `desktop/src/main/resources/fonts/` | SIL Open Font License 1.1 | See `OFL-Manrope.txt` in same directory |
| JetBrains Mono Regular | TTF in `desktop/src/main/resources/fonts/` | SIL Open Font License 1.1 | See `OFL-JetBrainsMono.txt`; https://github.com/JetBrains/JetBrainsMono |

The web player uses **system UI fonts** only (no webfont files).

## Icons

| Asset set | License | Notes |
|-----------|---------|--------|
| SVG icons in `desktop/src/main/resources/icons/` | **Project original** (Solocrew) | Simple stroke icons authored for Crewcast; no third-party icon font |

## Optional binary downloaded at runtime (not in this repo)

| Component | License | Source |
|-----------|---------|--------|
| cloudflared | Apache-2.0 | https://github.com/cloudflare/cloudflared — downloaded from official GitHub releases when tunnel is enabled and binary not on `PATH` |
| ffmpeg / ffprobe | **Per platform** (see below) | **Downloaded only at user request** — not bundled or distributed by Crewcast with the MIT app. See [docs/FFMPEG-SOURCES.md](docs/FFMPEG-SOURCES.md). |

**ffmpeg / ffprobe license by platform (chosen builds):**

| Platform | Source | License |
|----------|--------|---------|
| Windows x64 | `github.com` — BtbN `win64-lgpl` | **LGPL v2.1+** |
| Linux x64 / arm64 | `github.com` — BtbN `linux64-lgpl` / `linuxarm64-lgpl` | **LGPL v2.1+** |
| macOS (Intel & Apple Silicon) | **ffmpeg.martin-riedl.de** (individual maintainer; SHA-256 from same host) | **GPL v2+** (verified arm64 build: `--enable-gpl`, libx264/libx265) |

**Extraction:** ffmpeg zip / `.tar.xz` and cloudflared `.tgz` are unpacked with **Apache Commons Compress** and **SafeArchive** (JDK zip for tests only). Linux/macOS binary modes from archive entries are applied where supported.

## Design mockups (`docs/design/desktop-ui/`)

| Component | License | Source |
|-----------|---------|--------|
| React / ReactDOM (vendor bundles) | MIT | https://react.dev/ |
| dc-runtime `support.js` | (generated tooling) | Bundled for offline mockup preview only |

---

## License compatibility with MIT (Crewcast)

| Category | MIT-compatible? | Notes |
|----------|-----------------|-------|
| Apache-2.0, MIT, BSD-3-Clause (runtime Maven deps) | Yes | Standard permissive licenses |
| Logback (EPL-1.0 **or** LGPL-2.1) | **Yes (accepted choice)** | Used as a logging implementation on the classpath via Spring Boot, not modified. Recipients may rely on **LGPL-2.1** for linking/distribution of combined works; EPL applies if you modify Logback itself. Same pattern as countless MIT/Apache applications using Spring Boot. |
| SIL OFL (bundled fonts) | Yes | Fonts may be bundled with software; do not sell the font files standalone |
| cloudflared (optional download) | Yes | Apache-2.0; separate binary, not merged into the MIT source tree |
| ffmpeg (optional download) | N/A | Crewcast spawns ffmpeg as a separate process; if **you** redistribute downloaded binaries, LGPL/GPL terms apply to **those binaries**—not to Crewcast MIT source |
| Playwright (tests only) | Yes | Not required to ship with the app |

**Nothing in the table above requires Crewcast’s own source to be licensed under GPL or another copyleft license** when distributed under MIT, provided bundled components are used unmodified and their notices are preserved (this file).
