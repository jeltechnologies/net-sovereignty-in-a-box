# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Docker Compose stack that runs a personal VLESS+REALITY proxy (Xray-core) fronted by a small Spring
Boot status portal. Both `xray` (443) and `portal` (16810) are published directly on the host.

```
init (teddysun/xray base, ./init) — one-shot init container, generates client identity + REALITY keypair on first start
xray  (teddysun/xray)  — VLESS+REALITY proxy server, config-driven, port 443
portal (Spring Boot 4.1 / Java 21, ./portal) — status page + JSON/QR endpoints for the VLESS link, port 8080 internally, published on 16810
```

All three are wired together in `docker-compose.yaml`. There is no test suite or linter in this repo.
`portal` is a Maven project (the only build tooling here); `init`'s `generate-identity.sh` is a plain
shell script.

## Releases

Pushing a `v*.*.*` tag triggers `.github/workflows/release.yml`, which builds and pushes the `portal`
and `init` images to GHCR (`ghcr.io/<owner>/<repo>-portal`, `ghcr.io/<owner>/<repo>-init`, tagged with
both the version and `latest`) and creates a GitHub Release with auto-generated notes. `xray` is not
built here — it stays the upstream `teddysun/xray:latest` image.

## Commands

```bash
# Start/rebuild the whole stack
docker compose up -d --build

# Rebuild just the portal after editing its Java sources
docker compose up -d --build portal

# Logs
docker compose logs -f xray
docker compose logs -f portal

# Run the portal locally without Docker (needs an identity.env at /etc/xray/identity.env,
# see "Client identity" below, plus the same XRAY_PORT env var docker-compose.yaml sets)
cd portal && mvn -q -DskipTests package
XRAY_PORT=443 PORTAL_USER_NAME=admin PORTAL_PASSWORD=changeme java -jar target/portal.jar
```

## Architecture notes

- **There is no `.env` file, and there should not be one.** `SNI_DOMAIN`, `PORTAL_USER_NAME`, and
  `PORTAL_PASSWORD` are hardcoded literals directly in `docker-compose.yaml`'s `environment:` blocks
  (not `${VAR:-default}` interpolation) — customizing any of these means editing `docker-compose.yaml`
  directly, then recreating the affected service (e.g. `docker compose up -d portal`) to pick up the
  change. Don't reintroduce a `.env` file/`.env.example` or `${VAR}` substitution for these — that's a
  deliberate simplification, not an oversight (this is a single-user personal proxy stack with no real
  secrets to separate out).

- **The portal is protected by HTTP Basic Auth, `PORTAL_USER_NAME`/`PORTAL_PASSWORD`, both left blank
  in `docker-compose.yaml` on purpose** — the portal exposes the UUID, REALITY keys, and VLESS link, so
  operators are forced to pick real credentials by editing those two lines directly rather than running
  with a guessable default like `admin`/`admin`. Until both are set, every route (including `/qr` and
  `/json`) returns a `500` error page instead of serving connection details; `PortalAuthFilter` checks
  this by testing whether both env vars are non-empty (`PortalProperties#authConfigured`). Once both
  are set and the service is recreated, every route requires HTTP Basic Auth — browsers show their
  native login prompt via the `401` + `WWW-Authenticate` response — compared with
  `MessageDigest.isEqual` in `PortalAuthFilter`.

- **Client identity — UUID, short ID, SNI/serverName, and the REALITY X25519 keypair — is fully
  auto-generated on first start**, nothing is hardcoded. All three services mount the top-level
  `./data` directory (gitignored in full — it's empty at first checkout) to `/etc/xray`; `data/config.json`
  and `data/identity.env` are runtime-generated and never tracked. The static skeleton +
  placeholder-identity template lives at `init/config.template.json` and is baked into the `init` image
  (`COPY`'d to `/config.template.json`) rather than read off the volume, since on a fresh checkout
  `./data` doesn't exist yet — there's nothing to mount a template from. The `init` service
  (`init/generate-identity.sh`, image built `FROM teddysun/xray` so the real `xray` binary is
  available) runs once before `xray`/`portal` start: if `data/config.json` doesn't exist yet it's copied
  from the baked-in template, then (unless `identity.env` already exists) it generates a UUID, a random
  16-hex-char short ID, an SNI domain (`SNI_DOMAIN`, hardcoded to `swcdn.apple.com` in
  `docker-compose.yaml` — edit it there to change), and a REALITY keypair via `xray x25519` (output parsed from its `PrivateKey:` / `Password (PublicKey):`
  lines — reparse both if a future `xray` version changes that wording). All of these are patched into
  `config.json` via `jq` and written to `identity.env` (including `PUBLIC_KEY`) as the "already
  generated" marker. `portal` reads `identity.env` directly off a read-only mount of `./data` (not
  Compose `env_file` — that's resolved before the generator container can create the file, a
  chicken-and-egg problem). On every later start, `init` sees `identity.env` already exists and
  exits immediately — identity is stable across restarts. To force a new identity and keypair (e.g. to
  fully rotate access), delete `data/identity.env` (and optionally `data/config.json`) and restart
  the stack — remember to `docker compose restart xray` too, since it only reads `config.json` at its
  own startup and won't notice an in-place edit on its own.
- **The generator script writes into the existing file inode (`cat > file`), never `mv`** — `mv` from
  inside the (root) container replaces the inode and leaves `config.json` root-owned and `0600`,
  unreadable by the host user. If you edit `generate-identity.sh`, preserve this.
- **`portal` is a Spring Boot 4.1 (Spring Framework 7) / Java 21 Maven project**, package
  `com.jeltechnologies.portal`. No Lombok — config binding and data shapes use plain Java records
  (`PortalProperties`, `Identity`, `ConnectionInfo`). Layout: `config/` (env-var-backed
  `@ConfigurationProperties` record), `identity/` (parses `identity.env`), `connection/` (public-IP
  lookup + the one-shot `ConnectionInfoProvider` bean built at startup), `security/PortalAuthFilter`
  (a plain `jakarta.servlet.Filter` — no Spring Security dependency, kept in the spirit of the
  project's minimal-deps philosophy), `web/` (the `PortalController` routes plus `QrCodeService`,
  which wraps ZXing). The UI is a single Thymeleaf template
  (`src/main/resources/templates/index.html`) with inline `<style>`/`<script>` — no separate CSS/JS
  build step, matching this project's preference for keeping the portal to as few moving parts as
  practical. Keep changes in this style rather than pulling in more framework surface (e.g. Spring
  Security, a JS bundler) unless the scope genuinely grows to need it.
- **The public IP is not hardcoded anywhere.** At startup, `ConnectionInfoProvider`'s constructor
  fetches the public IP from `https://api.ipify.org` (via `java.net.http.HttpClient`) and uses it to
  build the VLESS link once, held in memory for the life of the process. If that fetch — or identity
  loading — fails, the exception propagates out of Spring context startup; `PortalApplication.main`
  catches it, logs, and calls `System.exit(1)`, relying on Docker's `restart: unless-stopped` to retry.
  `PUBLIC_IP`/`VLESS_LINK` are not environment variables — don't reintroduce them as hardcoded env vars
  in `docker-compose.yaml`.
- Portal routes: `/` (HTML status page), `/json` and `/api` (connection info as JSON), `/qr` (PNG QR
  code of the VLESS link). All other connection details read from env vars set by `docker-compose.yaml`;
  there's no config file for the portal.
- `data/config.json` blocks private and China (`geoip:cn`) IP ranges via routing rules — keep
  this in mind if debugging connectivity that looks like a routing rejection rather than a proxy failure.
