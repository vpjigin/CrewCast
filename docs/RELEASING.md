# Releasing Crewcast

Installers are built in GitHub Actions ([`.github/workflows/release.yml`](../.github/workflows/release.yml)). Each platform produces a fixed filename under `dist/`:

| Artifact |
|----------|
| `Crewcast-macos-arm64.dmg` |
| `Crewcast-macos-x64.dmg` |
| `Crewcast-windows-x64.msi` |
| `Crewcast-linux-x64.deb` |

Set **`CREWCAST_RELEASE_VERSION`** (optional locally) to the tag without forcing a Maven bump — e.g. `1.0.0` or `v1.0.0-rc1`.

## Cut a release (tag)

1. Ensure **`main`** (or your release branch) passes CI (`mvn -B verify`).
2. Choose a tag **`v*`** (example: `v1.0.0`). Tags with a **hyphen** (example: `v1.0.0-rc1`) are published as **GitHub prereleases**.
3. Create and push the tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
4. The **Release** workflow builds all four installers, writes **`SHA256SUMS.txt`**, and creates a **GitHub Release** for that tag (not a draft).

## Dry run (no release published)

Push a branch under **`ci-dryrun/`** (works even before `release.yml` is on the default branch, as long as the workflow file exists on the branch you push):

```bash
git push origin HEAD:refs/heads/ci-dryrun/my-packaging-test
```

1. Open **Actions** → the **Release** run for that push.
2. Download installers from **workflow artifacts** (one per platform).
3. The **publish** job is skipped — it runs only for **`v*`** tag pushes.

Optional: **workflow_dispatch** (enter a version, build artifacts only) appears in the Actions UI after **`release.yml` is on the default branch**. It also does not publish a release.

## Fix a bad release

1. On GitHub: **Releases** → open the bad release → **Delete release** (choose whether to delete the tag).
2. Locally (if the tag remains):
   ```bash
   git push origin :refs/tags/v1.0.0
   git tag -d v1.0.0
   ```
3. Fix the problem, tag again, and push.

## Local packaging (optional)

```bash
./packaging/build-macos.sh          # macOS only
./packaging/build-linux.sh          # Linux x64 only
pwsh packaging/build-windows.ps1    # Windows x64 only
```

Use the same **`CREWCAST_RELEASE_VERSION`** as the tag when testing version strings.
