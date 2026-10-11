# Privacy Policy

> **DRAFT — for legal review.** Not effective until published and dated by Solocrew.

Crewcast is self-hosted software you run on your own computer. Solocrew does not
operate a hosted service that stores your videos.

**Provided as is, without warranty of any kind**, consistent with the MIT License
(see [LICENSE](LICENSE)).

## What Crewcast stores locally

When you run Crewcast, data may be written under `~/.crewcast/` on your machine,
including settings (owner-only permissions on Unix), optional transcoded audio cache
files, and an optional downloaded `cloudflared` binary. See [docs/PRIVACY-INVENTORY.md](docs/PRIVACY-INVENTORY.md)
for detail.

The private link key (`?t=`) is generated when you start sharing and kept in memory
only—it is not stored in `settings.json`. Old settings files that contain legacy token
fields are migrated automatically on the next save.

While sharing is active, the app keeps a short-lived in-memory list of active viewers
(roughly the last 30 seconds) to display in the desktop UI. This list is not saved to
disk by Crewcast.

## Network use

- **Same Wi‑Fi / localhost**: Browsers talk directly to your Crewcast server.
- **Public link**: If you enable the Cloudflare Quick Tunnel, traffic between viewers
  and your computer passes through Cloudflare’s network. Cloudflare’s policies apply to
  that leg.
- **First-time tunnel setup**: Crewcast may download `cloudflared` from Cloudflare’s
  official GitHub releases over HTTPS.

The built-in web player does not load scripts, fonts, or images from third-party CDNs.

## What Solocrew does not collect

We do not receive your videos, settings, or viewer lists from the application. There is
no Solocrew account or analytics beacon in the open-source app described in this
repository.

## Your responsibilities

You choose which files to share and who receives links. Protect access tokens (`?t=`)
like passwords when “require private key” is enabled.

## Contact

Privacy questions (draft): **solocrew.me@gmail.com**

## Governing law

This draft is intended to be governed by the laws of **India** once finalized.

## Changes

> **DRAFT:** Add effective date and change notice process before publication.
