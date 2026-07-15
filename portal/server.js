'use strict';
const http   = require('http');
const url    = require('url');
const fs     = require('fs');
const crypto = require('crypto');
const QRCode = require('qrcode');

const env = k => process.env[k] || '';
const XRAY_PORT = env('XRAY_PORT') || '443';
const PORTAL_USER_NAME = env('PORTAL_USER_NAME');
const PORTAL_PASSWORD  = env('PORTAL_PASSWORD');
const AUTH_CONFIGURED  = Boolean(PORTAL_USER_NAME && PORTAL_PASSWORD);

const PORT = 8080;
const IDENTITY_FILE = '/etc/xray/identity.env';

let UUID       = '';
let SHORT_ID   = '';
let SNI_DOMAIN = '';
let PUBLIC_KEY = '';
let PUBLIC_IP  = '';
let VLESS_LINK = '';

function loadIdentity() {
  const raw = fs.readFileSync(IDENTITY_FILE, 'utf8');
  const vars = {};
  for (const line of raw.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf('=');
    if (idx === -1) continue;
    vars[trimmed.slice(0, idx)] = trimmed.slice(idx + 1);
  }
  return vars;
}

async function fetchPublicIp() {
  const res = await fetch('https://api.ipify.org');
  if (!res.ok) throw new Error('ipify returned status ' + res.status);
  const ip = (await res.text()).trim();
  if (!ip) throw new Error('ipify returned an empty response');
  return ip;
}

async function fetchLocation(ip) {
  try {
    const res = await fetch(`http://ip-api.com/json/${ip}?fields=status,country,city`);
    if (!res.ok) throw new Error('ip-api.com returned status ' + res.status);
    const data = await res.json();
    if (data.status !== 'success' || !data.country) throw new Error('ip-api.com lookup failed');
    return data.city ? `${data.city}, ${data.country}` : data.country;
  } catch (err) {
    console.error('Location lookup failed, falling back to default label:', err.message);
    return 'Xray-REALITY';
  }
}

const esc = s => String(s)
  .replace(/&/g,'&amp;').replace(/</g,'&lt;')
  .replace(/>/g,'&gt;').replace(/"/g,'&quot;');

function safeEqual(a, b) {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  // Compare against a same-length buffer first so mismatched lengths don't
  // short-circuit before timingSafeEqual, which throws on length mismatch.
  if (bufA.length !== bufB.length) {
    crypto.timingSafeEqual(bufA, bufA);
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

function isAuthorized(req) {
  const header = req.headers['authorization'] || '';
  if (!header.startsWith('Basic ')) return false;
  const decoded = Buffer.from(header.slice(6), 'base64').toString('utf8');
  const idx = decoded.indexOf(':');
  if (idx === -1) return false;
  const user = decoded.slice(0, idx);
  const pass = decoded.slice(idx + 1);
  return safeEqual(user, PORTAL_USER_NAME) && safeEqual(pass, PORTAL_PASSWORD);
}

async function handleRequest(req, res) {
  const { pathname } = url.parse(req.url);

  if (!AUTH_CONFIGURED) {
    res.writeHead(500, { 'Content-Type': 'text/plain' });
    res.end('Portal authentication is not configured. Set PORTAL_USER_NAME and PORTAL_PASSWORD in docker-compose.yaml and restart the stack.');
    return;
  }

  if (!isAuthorized(req)) {
    res.writeHead(401, {
      'Content-Type': 'text/plain',
      'WWW-Authenticate': 'Basic realm="Xray Portal", charset="UTF-8"',
    });
    res.end('Authentication required');
    return;
  }

  if (pathname === '/json' || pathname === '/api') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      protocol: 'vless', uuid: UUID, address: PUBLIC_IP,
      port: Number(XRAY_PORT), flow: 'xtls-rprx-vision',
      security: 'reality', sni: SNI_DOMAIN, publicKey: PUBLIC_KEY,
      shortId: SHORT_ID, fingerprint: 'chrome', network: 'tcp',
      vlessLink: VLESS_LINK,
    }, null, 2));
    return;
  }

  if (pathname === '/qr') {
    try {
      const buf = await QRCode.toBuffer(VLESS_LINK, {
        errorCorrectionLevel: 'M', scale: 6, margin: 2,
        color: { dark: '#000000', light: '#ffffff' },
      });
      res.writeHead(200, { 'Content-Type': 'image/png' });
      res.end(buf);
    } catch (e) {
      res.writeHead(500); res.end('QR generation failed: ' + e.message);
    }
    return;
  }

  if (pathname === '/' || pathname === '/index.html') {
    const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<meta name="theme-color" content="#0f172a"/>
<title>Xray Config Portal</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
  background:#0f172a;color:#e2e8f0;min-height:100vh;
  display:flex;flex-direction:column;align-items:center;padding:20px 14px}
h1{font-size:1.4rem;font-weight:700;color:#38bdf8;margin-bottom:2px;text-align:center}
.subtitle{font-size:.8rem;color:#64748b;margin-bottom:24px;text-align:center}
.card{background:#1e293b;border:1px solid #334155;border-radius:14px;
  padding:20px;width:100%;max-width:460px;margin-bottom:16px}
.card h2{font-size:.75rem;font-weight:700;color:#7dd3fc;
  text-transform:uppercase;letter-spacing:.08em;margin-bottom:14px}
.qr-wrap{display:flex;justify-content:center;background:#fff;
  border-radius:10px;padding:14px;margin-bottom:10px}
.qr-wrap img{width:min(240px,80vw);height:auto}
.qr-hint{text-align:center;font-size:.72rem;color:#64748b;margin-top:4px}
.row{margin-bottom:10px}.row:last-child{margin-bottom:0}
.lbl{font-size:.65rem;color:#64748b;text-transform:uppercase;
  letter-spacing:.07em;margin-bottom:3px}
.val{background:#0f172a;border-radius:6px;padding:6px 10px;
  font-family:'SF Mono',Consolas,monospace;font-size:.78rem;
  color:#cbd5e1;word-break:break-all;line-height:1.4}
.badges{display:flex;flex-wrap:wrap;gap:6px;margin-top:4px}
.badge{background:#0e4f6b;color:#7dd3fc;border-radius:20px;
  font-size:.65rem;font-weight:700;padding:2px 8px;letter-spacing:.04em}
.link-box{background:#0f172a;border-radius:8px;padding:10px;
  font-size:.72rem;word-break:break-all;color:#94a3b8;
  font-family:monospace;line-height:1.5;cursor:pointer;
  border:1px solid #1e3a5f;transition:border-color .2s}
.link-box:hover{border-color:#38bdf8}
.copy-hint{font-size:.65rem;color:#475569;text-align:right;margin-top:4px}
.ep-row{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px}
.ep{color:#38bdf8;font-size:.78rem;text-decoration:none;
  background:#0f172a;padding:5px 14px;border-radius:6px;
  border:1px solid #1d4ed8;transition:background .15s}
.ep:hover{background:#1d4ed8;color:#fff}
footer{font-size:.68rem;color:#334155;margin-top:auto;
  padding-top:20px;text-align:center}
</style>
</head>
<body>
<h1>⚡ Xray</h1>
<p class="subtitle">VLESS + REALITY — scan to import</p>
<div class="card">
  <h2>📱 QR Code</h2>
  <div class="qr-wrap"><img src="/qr" alt="VLESS QR Code" loading="lazy"/></div>
  <p class="qr-hint">v2rayNG · Shadowrocket · Hiddify · Nekoray</p>
</div>
<div class="card">
  <h2>⚙️ Connection Details</h2>
  <div class="row"><div class="lbl">Protocol / Mode</div>
    <div class="badges"><span class="badge">VLESS</span>
    <span class="badge">REALITY</span><span class="badge">xtls-rprx-vision</span>
    <span class="badge">TCP</span></div></div>
  <div class="row"><div class="lbl">Server</div>
    <div class="val">${esc(PUBLIC_IP)}</div></div>
  <div class="row"><div class="lbl">Port</div>
    <div class="val">${esc(XRAY_PORT)}</div></div>
  <div class="row"><div class="lbl">UUID</div>
    <div class="val">${esc(UUID)}</div></div>
  <div class="row"><div class="lbl">SNI / Server Name</div>
    <div class="val">${esc(SNI_DOMAIN)}</div></div>
  <div class="row"><div class="lbl">Public Key (pbk)</div>
    <div class="val">${esc(PUBLIC_KEY)}</div></div>
  <div class="row"><div class="lbl">Short ID (sid)</div>
    <div class="val">${esc(SHORT_ID)}</div></div>
  <div class="row"><div class="lbl">Fingerprint</div>
    <div class="val">chrome</div></div>
</div>
<div class="card">
  <h2>🔗 VLESS Share Link</h2>
  <div class="link-box" id="vlink" title="Click to copy"
       onclick="navigator.clipboard.writeText(this.dataset.link);
                this.style.borderColor='#22c55e';
                document.getElementById('ch').textContent='Copied!';
                setTimeout(()=>{this.style.borderColor='';
                document.getElementById('ch').textContent='Click to copy'},2000)"
       data-link="${esc(VLESS_LINK)}">${esc(VLESS_LINK)}</div>
  <div class="copy-hint" id="ch">Click to copy</div>
</div>
<div class="card">
  <h2>🛠 API</h2>
  <div style="font-size:.78rem;color:#64748b;margin-bottom:4px">For automation and sharing</div>
  <div class="ep-row">
    <a class="ep" href="/json">/json</a>
    <a class="ep" href="/qr">/qr (PNG)</a>
  </div>
</div>
<footer>Xray-core · VLESS+REALITY · Auto-generated</footer>
</body>
</html>`;
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not found');
}

async function start() {
  if (!AUTH_CONFIGURED) {
    console.warn('PORTAL_USER_NAME and/or PORTAL_PASSWORD not set — the portal will start, but every request will return an error until both are configured in docker-compose.yaml.');
  }

  const identity = loadIdentity();
  UUID       = identity.UUID;
  SHORT_ID   = identity.SHORT_ID;
  SNI_DOMAIN = identity.SNI_DOMAIN;
  PUBLIC_KEY = identity.PUBLIC_KEY;

  PUBLIC_IP = await fetchPublicIp();
  const location = await fetchLocation(PUBLIC_IP);
  VLESS_LINK = `vless://${UUID}@${PUBLIC_IP}:${XRAY_PORT}?encryption=none&flow=xtls-rprx-vision&security=reality&sni=${SNI_DOMAIN}&fp=chrome&pbk=${PUBLIC_KEY}&sid=${SHORT_ID}&type=tcp#${encodeURIComponent(location)}`;

  http.createServer((req, res) => {
    handleRequest(req, res).catch(err => {
      console.error(err);
      if (!res.headersSent) { res.writeHead(500); }
      res.end('Internal error');
    });
  }).listen(PORT, '0.0.0.0', () => {
    console.log('Xray portal listening on :' + PORT);
    console.log('Public IP: ' + PUBLIC_IP);
    console.log('VLESS link: ' + VLESS_LINK);
  });
}

start().catch(err => {
  console.error('Failed to determine public IP, cannot start portal:', err);
  process.exit(1);
});
