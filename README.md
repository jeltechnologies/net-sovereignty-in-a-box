# Net Sovereignty in a Box

A self-hosted VLESS+REALITY proxy, deployed in minutes with zero networking expertise.

## Why

China's Great Firewall and the people routing around it have been locked in a cat-and-mouse game for 25+ years: every time regulators learn to detect a proxy technique, engineers ship the next one. **REALITY** is the current state of the art in that arms race — instead of hiding traffic, it disguises your connection as an ordinary visit to a trusted site (e.g. the regulator's own servers), indistinguishable from millions of other people visiting that same site. There's nothing fake to notice and no unusual pattern to flag — which is why it holds up where earlier VPN protocols get detected and blocked. You never actually connect to or use the camouflage domain — it's disguise only, not a real destination for your traffic.

This isn't just a China problem. The UK's Online Safety Act already requires ISPs and platforms to restrict access by identity and location, and the EU is moving toward similar digital-identity and content-control rules. The playbook is always the same: block specific sites first to "protect the people", then pressure ISPs into blocking the VPNs people use to route around that. Conventional VPNs are easy to fingerprint and blocklist once a regulator decides to go after them. Running your own REALITY proxy now — while it's simple and unregulated — means you own the server and the disguise domain, and aren't dependent on a commercial VPN provider that can be pressured or blocked wholesale.

## What you get

- A cleaner alternative to Tor, which a growing number of people in the UK are starting to turn to — Tor is also where a lot of shady and illegal activity lives, whereas this is just your own, family-friendly uncensored internet access.
- A compete solutiom, which takes about 15 minutes to deploy, with miminal configuration.
- A private VLESS+REALITY proxy, with a unique identity and keypair generated on first start — nothing shared, nothing hardcoded.
- A web portal with a ready-to-scan QR code and connection link.
- Full ownership: your VPS, your IP, your camouflage domain — no third-party VPN provider to trust or be pressured.

## What you need

- A VPS in an uncensored country (e.g. [Vultr](https://www.vultr.com/) or [DigitalOcean](https://www.digitalocean.com/), US region). Cheapest tier is enough, around **€5/month**.
- Docker installed on that VPS.

The stack generates its own identity, keys, and config automatically on first start.

## Deployment

1. **Source the VPS.** Spin up a small cloud server at a provider of your choice (e.g. Vultr, DigitalOcean), in a jurisdiction without internet censorship. The cheapest tier is enough.

2. **Test the ping.** Before doing anything else, go back to your home connection, switch off any VPN, and ping the VPS's IP address:
   ```
   ping <VPS_IP>
   ```
   If the ping fails or times out, your ISP is likely already blocking that IP range. Go back to your provider, request a new IP (or destroy and recreate the VPS), and test again. If it keeps failing, try a different provider or region entirely. Don't proceed to the next step until this succeeds.

3. **Install Docker** on the VPS.

4. **Add the `docker-compose.yaml` file** to the VPS. Download it from [here](https://raw.githubusercontent.com/jeltechnologies/net-sovereignty-in-a-box/main/docker-compose.yaml) and place it in a folder on your VPS.

5. **Set portal credentials.** Open `docker-compose.yaml` and fill in `PORTAL_USER_NAME` and `PORTAL_PASSWORD` under the `portal` service's `environment:` section — they're blank by default, and the portal refuses to show your connection details until both are set.

6. **Run it:**
   ```
   docker compose up -d
   ```
   On first start, the stack automatically generates a unique client identity and REALITY keypair — no manual configuration required beyond the portal credentials above.

7. **Open the portal.** In a browser, go to:
   ```
   http://<VPS_IP>:16810
   ```
   Log in with the `PORTAL_USER_NAME`/`PORTAL_PASSWORD` you set in step 5.

## How to connect

The portal displays a QR code and connection link for your proxy.

- **Phone (iOS / Android):** use a VLESS+REALITY-compatible client — this has been tested with **V2Box**, available on both the App Store and Google Play. Open the app and scan the QR code shown by the portal.
- **Windows:** use **Hiddify**. Copy the connection link from the portal and paste it into the client.

Other VLESS+REALITY-compatible clients will also work — V2Box and Hiddify are simply the ones this project has been verified against.

## Disclaimer

This reflects the current state of the art as of July 2026. Censorship is a cat-and-mouse game — this will eventually stop working, and something else will replace it.
