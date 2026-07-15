# Net Sovereignty in a Box

This repository provides internet sovereignty and anti-censorship, powered by battle-tested technology, deployed in minutes.

## Why this exists

For over a decade, China has run the most sophisticated internet censorship system in the world — the Great Firewall. In that same time, the people living behind it have run the world's most sophisticated censorship-resistance experiments. Every time the Firewall learned to detect and block a new proxy technique, engineers built the next one. A technology called **REALITY** is the current state of that arms race: instead of hiding traffic and hoping it isn't noticed, it disguises your connection as an ordinary, everyday visit to a trusted, high-traffic website (think Microsoft or Apple's own servers). To a censor doing deep packet inspection, your traffic looks indistinguishable from millions of other people innocently loading a legitimate site — because in the parts that matter, it genuinely uses that site's real TLS certificate and handshake. There's no fake certificate to fingerprint and no suspicious pattern to flag. This is why REALITY has held up where earlier protocols eventually got detected and blocked.

> **Note:** the camouflage domain (e.g. `www.microsoft.com`) is only used for disguise — traffic is never routed through it.

## Why this project was made

There are many tutorials and information available on the internet on V2Ray and REALITY, but this material is either very technical, or takes many manual steps like file editing and troubleshooting. The best material is written in languages of countries that apply active censorship, like Chinese. This project aims to make it extremely easy to deploy a complete solution, without needing networking expertise.

## Why this matters beyond China

Internet censorship and control is introduced following a playbook that is well established: first block specific sites and services "to protect the people", then when people route around that with VPNs, pressure the infrastructure providers — the ISPs — into blocking the VPNs themselves. China spent years doing exactly this, and each time, camouflage-based protocols like REALITY kept working precisely because they don't look like a VPN at the network level in the first place.

The United Kingdom has already moved first, with age-verification and content-blocking rules under the Online Safety Act now requiring ISPs and platforms to restrict access based on identity and location. The European Union is moving toward its own tightening framework of digital regulation, content controls, and age/identity verification mandates, and it is reasonable to expect the EU to follow the same path the UK has already started down. As these rules mature, the same pressure will eventually land on ISPs: not just blocking specific content, but blocking the tools people use to route around blocks — the same trajectory China already walked. Conventional VPN protocols are relatively easy to fingerprint and blocklist once a regulator decides to go after them.

The value of deploying your own REALITY-based proxy now, while it's simple and unregulated, is that you're not scrambling to react once restrictions land. You own the server, you own the disguise domain, and you're not dependent on a commercial VPN provider who can be pressured, subpoenaed, or blocked wholesale. This lets you stay a step ahead of legislation rather than a step behind it.

## What you get

- A private VLESS+REALITY proxy server, generated with a unique identity and keypair on first start — nothing shared, nothing hardcoded, nothing to leak to other users of a public service.
- A lightweight web portal that hands you a ready-to-scan QR code and connection link, so setup on your devices takes seconds.
- Full ownership: it's your VPS, your IP, your camouflage domain. No third party sees your traffic or can be compelled to hand over logs you never created.

## What you need to run it

- **A VPS in a country without internet censorship** — a small cloud server from a mainstream provider such as [Vultr](https://www.vultr.com/) or [DigitalOcean](https://www.digitalocean.com/), located somewhere like the United States. The cheapest tier is enough — currently around **€5/month**.
- **Docker** installed on that VPS.
- That's it on the infrastructure side — the stack generates its own identity, keys, and configuration automatically on first start.

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

This solution reflects the most up to date and sophisticated stack in July 2026. Censorship is a cat and mouse game and eventually this solution will cease working. When it does, there will be new solutions. Carpe Diem, enjoy while it's still working.
