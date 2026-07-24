# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Docker Compose stack that runs a personal VLESS+REALITY proxy (Xray-core) fronted by a small Spring
Boot status portal. Both `xray` (443) and `portal` (16810, HTTPS-only) are published directly on the
host; the portal also publishes port 80, used solely for Let's Encrypt's ACME HTTP-01 challenge.

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

# Run the portal locally without Docker (needs an identity.env AND a tls/active.{crt,key}
# pair at /etc/xray/, see "Client identity" and "TLS / HTTPS" below, plus the same
# XRAY_PORT env var docker-compose.yaml sets)
cd portal && mvn -q -DskipTests package
XRAY_PORT=443 PORTAL_USER_NAME=admin PORTAL_PASSWORD=changeme java -jar target/portal.jar
```

## Architecture notes

- **Configuration lives in a gitignored `.env` file, not hardcoded in `docker-compose.yaml`.**
  `SNI_DOMAIN`, `DOMAIN`, `PORTAL_USER_NAME`, and `PORTAL_PASSWORD` are read via `${VAR:-default}`
  interpolation in `docker-compose.yaml`'s `environment:` blocks; the actual values go in `.env` at
  the repo root (copy `.env.example` to `.env` and fill it in). `.env` is in `.gitignore` and must
  never be committed — a real `PORTAL_PASSWORD` was accidentally committed to git history once
  (see git log around 2026-07-24) because it lived directly in `docker-compose.yaml`; `.env` plus
  `.gitignore` is the fix, since forgetting to gitignore a file is a much rarer mistake than a
  manually-configured git clean filter silently no-op'ing (the previous approach, removed in the same
  incident). Customizing any of these means editing `.env`, then recreating the affected service
  (e.g. `docker compose up -d portal`) to pick up the change. Keep `.env.example` in sync with any new
  variable added to `docker-compose.yaml`'s `environment:` blocks.

- **The portal is protected by Basic Auth credentials, `PORTAL_USER_NAME`/`PORTAL_PASSWORD`, both left
  blank by default (unset in `.env.example`)** — the portal exposes the UUID, REALITY keys, and VLESS
  link, so operators are forced to pick real credentials by setting both in `.env` rather than running
  with a guessable default like `admin`/`admin`. Until both are set, every route (including `/qr` and
  `/json`) returns a `500` error page instead of serving connection details; `PortalAuthFilter` checks
  this by testing whether both env vars are non-empty (`PortalProperties#authConfigured`). Once both
  are set and the service is recreated, every route requires authentication, checked against
  `PORTAL_USER_NAME`/`PORTAL_PASSWORD` with `MessageDigest.isEqual` (`PortalCredentials`) either way —
  but the credentials can arrive two ways:
  - **Browsers** get a custom login page (`GET /login`, `web/LoginController`, `templates/login.html`)
    instead of the browser's native Basic Auth popup. `PortalAuthFilter` never sends a
    `WWW-Authenticate` challenge — a request with no valid `Authorization` header and no valid auth
    cookie is 302-redirected to `/login`. A successful `POST /login` sets an `HttpOnly`, `Secure`,
    `SameSite=Strict` cookie (`PortalAuthFilter.AUTH_COOKIE_NAME`, 30-day expiry) whose value is the
    exact same base64(`user:pass`) a real Basic Auth header would carry — same credential check either
    way, just delivered without the ugly native prompt.
  - **Scripts/`curl -u`** keep working unchanged: a request that already carries an `Authorization:
    Basic ...` header is validated directly (200 or 401, no redirect), so nothing programmatic that
    relied on real Basic Auth broke.

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
  16-hex-char short ID, an SNI domain (`SNI_DOMAIN`, defaulting to `swcdn.apple.com` — set it in
  `.env` to change), and a REALITY keypair via `xray x25519` (output parsed from its `PrivateKey:` / `Password (PublicKey):`
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
  unreadable by the host user. If you edit `generate-identity.sh`, preserve this. (The TLS block
  described below is the one exception that does rename — see why there.)

- **TLS / HTTPS.** The portal is HTTPS-only (`server.ssl.enabled: true` in `application.yml`); there
  is no plain-HTTP portal route anymore. The cert Tomcat serves always lives at the same two paths,
  `data/tls/active.crt` / `active.key` (mounted at `/etc/xray/tls`), referenced by a Spring Boot
  **SSL Bundle** (`spring.ssl.bundle.pem.portal`) with `reload-on-update: true` — a background file
  watcher hot-swaps a changed cert/key pair into Tomcat with **no restart**, which is what makes
  unattended renewal possible.
  - **Volume mount**: `portal`'s main `./data:/etc/xray:ro` mount is read-only, but
    `docker-compose.yaml` adds a second, more specific mount, `./data/tls:/etc/xray/tls` (no
    `:ro`), which overrides the parent for just that subdirectory — Docker resolves the more
    specific bind mount for any path under it. The portal genuinely needs to write here (to
    persist `acme-account.key`, and to install/renew Let's Encrypt certs), while `identity.env`/
    `config.json` stay read-only since the portal never touches those. If you ever see ACME
    renewal failing with a read-only-filesystem error, this mount is the first thing to check.
  - **Self-signed fallback**: `generate-identity.sh` generates `active.crt`/`active.key` with
    `openssl req -x509` the same way it's idempotent about `identity.env` — if the files already
    exist, it leaves them alone (checked independently of the identity block, so rotating one
    doesn't force-rotate the other). This is the one generator step that *does* write via a
    temp-file-then-`mv` inside `openssl`'s own `-out`/`-keyout` flags, not the script itself — fine
    here because, unlike `config.json`, nothing outside the `portal` container ever reads these
    files.
  - **Let's Encrypt via ACME (optional)**: set `DOMAIN` in `.env`
    (blank by default) to a real hostname whose DNS A/AAAA record points at this server, and forward
    port 80 to it (published as `80:8880`) — Let's Encrypt's HTTP-01 challenge requires port 80,
    which is why it's separate from the main `16810:8080` HTTPS port. `tls/AcmeCertificateService`
    drives this with `acme4j` (Apache 2.0, `org.shredzone.acme4j:acme4j-client`): on
    `ApplicationReadyEvent` (on a background thread, so a slow/unreachable CA can never delay or
    fail startup — the self-signed cert is already serving by then) and daily via `@Scheduled`, it
    checks whether `active.crt` already covers `domain` and has >30 days left; if not, it runs the
    full ACME order → HTTP-01 challenge → CSR → download flow and atomically replaces
    `active.crt`/`active.key` (`Files.move(..., ATOMIC_MOVE)`, so the SSL bundle watcher never sees
    a half-written pair). `tls/AcmeChallengeStore` + `tls/AcmeChallengeController` serve the
    `/.well-known/acme-challenge/{token}` response; `tls/PlainHttpConnectorCustomizer` adds the extra
    plain-HTTP Tomcat connector on `acme.http-port` (8880) that Let's Encrypt actually reaches. Any
    ACME failure (DNS not propagated, port 80 unreachable, rate limits) is caught and logged — it
    never crashes the app or disturbs the currently-active cert.
  - **`PortalAuthFilter` carves out two exceptions** for this to work: requests to
    `/.well-known/acme-challenge/**` skip both the "not configured" 500 and Basic Auth entirely
    (any connector); requests landing on the plain-HTTP connector (`request.getLocalPort() ==
    acme.http-port`) for *any other* path get an immediate 404 — this is what stops the insecure
    port 80 from ever serving real portal content or prompting for Basic Auth credentials in
    cleartext, even though Tomcat's servlet context is shared across both connectors.
  - **`xray` keeps sole ownership of port 443.** REALITY's camouflage depends on port 443 behaving
    exactly like a normal HTTPS server for the disguise domain — don't try to multiplex the portal
    onto 443 via an SNI router or similar; that puts new infrastructure directly in the REALITY
    traffic path for a "no port number in the URL" convenience that isn't worth the risk here.
  - **Testing against Let's Encrypt without burning production rate limits**: set
    `ACME_DIRECTORY_URL=acme://letsencrypt.org/staging` (not exposed in `docker-compose.yaml` by
    default; add it under `portal.environment` temporarily) to point `acme4j` at the staging
    directory, which issues untrusted-but-real certs against the same flow.
- **`portal` is a Spring Boot 4.1 (Spring Framework 7) / Java 21 Maven project**, package
  `com.jeltechnologies.portal`. No Lombok — config binding and data shapes use plain Java records
  (`PortalProperties`, `AcmeProperties`, `Identity`, `ConnectionInfo`). Layout: `config/` (env-var-backed
  `@ConfigurationProperties` records), `identity/` (parses `identity.env`), `connection/` (public-IP
  lookup + the one-shot `ConnectionInfoProvider` bean built at startup), `security/` (`PortalAuthFilter`,
  a plain `jakarta.servlet.Filter` — no Spring Security dependency, kept in the spirit of the
  project's minimal-deps philosophy — plus `PortalCredentials`, the shared constant-time credential
  check both the filter and the login form use), `tls/` (self-signed-or-Let's-Encrypt HTTPS, see
  "TLS / HTTPS" below), `web/` (the `PortalController` routes, `LoginController` for `/login`, plus
  `QrCodeService`, which wraps ZXing). The UI is Thymeleaf templates
  (`src/main/resources/templates/index.html`, `login.html`) with inline `<style>`/`<script>` — no
  separate CSS/JS build step, matching this project's preference for keeping the portal to as few
  moving parts as
  practical. Keep changes in this style rather than pulling in more framework surface (e.g. Spring
  Security, a JS bundler) unless the scope genuinely grows to need it.
- **The public IP is not hardcoded anywhere.** At startup, `ConnectionInfoProvider`'s constructor
  fetches the public IP from `https://api.ipify.org` (via `java.net.http.HttpClient`) and uses it to
  build the VLESS link once, held in memory for the life of the process. If that fetch — or identity
  loading — fails, the exception propagates out of Spring context startup; `PortalApplication.main`
  catches it, logs, and calls `System.exit(1)`, relying on Docker's `restart: unless-stopped` to retry.
  `PUBLIC_IP`/`VLESS_LINK` are not environment variables — don't reintroduce them as hardcoded env vars
  in `docker-compose.yaml`.
- Portal routes (all HTTPS, all Basic-Auth-gated): `/` (HTML status page), `/json` and `/api`
  (connection info as JSON), `/qr` (PNG QR code of the VLESS link). Plus one unauthenticated route on
  the plain-HTTP port only: `/.well-known/acme-challenge/{token}` (see "TLS / HTTPS"). All other
  connection details read from env vars set by `docker-compose.yaml`; there's no config file for the
  portal.
- `data/config.json` blocks private and China (`geoip:cn`) IP ranges via routing rules — keep
  this in mind if debugging connectivity that looks like a routing rejection rather than a proxy failure.
