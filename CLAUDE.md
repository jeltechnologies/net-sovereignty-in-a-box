# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A 3-container Docker Compose stack that runs a personal VLESS+REALITY proxy (Xray-core) fronted by a
tiny Node.js status portal, tunneled to the internet via Pangolin/Newt (no public port exposure needed
except Xray's own 443).

```
identity (alpine, ./xray/init) — one-shot init container, generates client identity on first start
xray  (teddysun/xray)  — VLESS+REALITY proxy server, config-driven, port 443
portal (Node 20, ./portal) — status page + JSON/QR endpoints for the VLESS link, port 8080 (internal only)
newt  (fosrl/newt)     — Pangolin tunnel client, shares portal's network namespace
```

All four are wired together in `docker-compose.yml`. There is no build tooling, test suite, or linter
in this repo — `portal` and `identity`'s `generate-identity.sh` are the only things with actual code.

## Commands

```bash
# Start/rebuild the whole stack
docker compose up -d --build

# Rebuild just the portal after editing portal/server.js
docker compose up -d --build portal

# Logs
docker compose logs -f xray
docker compose logs -f portal
docker compose logs -f newt

# Run the portal locally without Docker (needs the same env vars docker-compose.yml sets)
cd portal && npm install
UUID=... PUBLIC_KEY=... SHORT_ID=... SNI_DOMAIN=... XRAY_PORT=443 node server.js
```

## Architecture notes

- **Client identity (UUID, short ID, SNI/serverName) is auto-generated on first start**, not hardcoded.
  `xray/config/config.json` and `xray/config/identity.env` are both gitignored — only
  `xray/config/config.template.json` (static skeleton + placeholder identity fields) is tracked. The
  `identity` service (`xray/init/generate-identity.sh`) runs once before `xray`/`portal` start: if
  `config.json` doesn't exist yet it's copied from the template, then (unless `identity.env` already
  exists) a UUID, a random 16-hex-char short ID, and one SNI domain picked at random from
  `www.cloudflare.com` / `www.microsoft.com` are generated, patched into `config.json` via `jq`, and
  written to `identity.env` as the "already generated" marker. `portal` reads `identity.env` directly
  off a read-only mount of `xray/config` (not Compose `env_file` — that's resolved before the generator
  container can create the file, a chicken-and-egg problem). On every later start, `identity` sees
  `identity.env` already exists and exits immediately — identity is stable across restarts. To force a
  new identity (e.g. to fully rotate access), delete `xray/config/identity.env` (and optionally
  `config.json`) and restart the stack.
- **The generator script writes into the existing file inode (`cat > file`), never `mv`** — `mv` from
  inside the (root) container replaces the inode and leaves `config.json` root-owned and `0600`,
  unreadable by the host user. If you edit `generate-identity.sh`, preserve this.
- **The REALITY keypair is still static**, not part of the auto-generated identity: `privateKey` in
  `xray/config/config.json` and the derived `PUBLIC_KEY` in the portal's `docker-compose.yml` env must
  stay consistent with each other — if you rotate the keypair, update both files together by hand.
- **`portal/server.js` is intentionally a single dependency-free-ish file** (only `qrcode` from npm) with
  no framework: raw `http.createServer`, manual routing by `pathname`, inline HTML/CSS template string
  for `/`. Keep changes in this style rather than introducing a framework unless the scope grows
  significantly.
- **The public IP is not hardcoded anywhere.** On startup, `server.js` fetches its own public IP from
  `https://api.ipify.org` and uses it to build `VLESS_LINK` in memory; if that fetch fails, the process
  exits and relies on Docker's `restart: unless-stopped` to retry. `PUBLIC_IP`/`VLESS_LINK` are no longer
  environment variables — don't reintroduce them as hardcoded env vars in `docker-compose.yml`.
- Portal routes: `/` (HTML status page), `/json` and `/api` (connection info as JSON), `/qr` (PNG QR
  code of the VLESS link). All other connection details read from env vars set by `docker-compose.yml`;
  there's no config file for the portal.
- `newt` uses `network_mode: "service:portal"`, so it shares portal's network namespace rather than
  getting its own — this is what lets Pangolin route traffic to the portal without publishing a host
  port for it. Only `xray`'s port 443 is published directly (`ports:` on the `xray` service); `portal` is
  `expose`-only.
- `xray/config/config.json` blocks private and China (`geoip:cn`) IP ranges via routing rules — keep
  this in mind if debugging connectivity that looks like a routing rejection rather than a proxy failure.
