# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A 3-container Docker Compose stack that runs a personal VLESS+REALITY proxy (Xray-core) fronted by a
tiny Node.js status portal, tunneled to the internet via Pangolin/Newt (no public port exposure needed
except Xray's own 443).

```
xray  (teddysun/xray)  — VLESS+REALITY proxy server, config-driven, port 443
portal (Node 20, ./portal) — status page + JSON/QR endpoints for the VLESS link, port 8080 (internal only)
newt  (fosrl/newt)     — Pangolin tunnel client, shares portal's network namespace
```

All three are wired together in `docker-compose.yml`. There is no build tooling, test suite, or linter
in this repo — `portal` is the only thing with actual code, and it's a single-file, dependency-light
HTTP server.

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

- **Credentials live in `docker-compose.yml` and `xray/config/config.json`, not in `.env` files.** The
  UUID, REALITY keys (`privateKey` in xray config vs. `publicKey`/`PUBLIC_KEY` in the portal env), and
  short ID must stay consistent across both files — the private key never appears in the portal env,
  only the derived public key does. If you rotate credentials, update both files together.
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
