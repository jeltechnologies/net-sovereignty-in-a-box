#!/bin/sh
set -eu

CONFIG_FILE=/etc/xray/config.json
TEMPLATE_FILE=/config.template.json
IDENTITY_FILE=/etc/xray/identity.env
TLS_DIR=/etc/xray/tls
TLS_CERT=$TLS_DIR/active.crt
TLS_KEY=$TLS_DIR/active.key

if [ -f "$IDENTITY_FILE" ]; then
  echo "identity.env already present, keeping existing identity."
else
  if [ ! -f "$CONFIG_FILE" ]; then
    cp "$TEMPLATE_FILE" "$CONFIG_FILE"
    chmod 644 "$CONFIG_FILE"
  fi

  UUID=$(cat /proc/sys/kernel/random/uuid)
  SHORT_ID=$(openssl rand -hex 8)

  SNI_DOMAIN=${SNI_DOMAIN:-www.microsoft.com}

  KEYPAIR=$(xray x25519)
  PRIVATE_KEY=$(echo "$KEYPAIR" | awk -F': ' '/^PrivateKey:/ {print $2}')
  PUBLIC_KEY=$(echo "$KEYPAIR" | awk -F': ' '/^Password \(PublicKey\):/ {print $2}')

  if [ -z "$PRIVATE_KEY" ] || [ -z "$PUBLIC_KEY" ]; then
    echo "Failed to parse REALITY keypair from 'xray x25519' output:" >&2
    echo "$KEYPAIR" >&2
    exit 1
  fi

  TMP=$(mktemp)
  jq \
    --arg uuid "$UUID" \
    --arg sid "$SHORT_ID" \
    --arg sni "$SNI_DOMAIN" \
    --arg pk  "$PRIVATE_KEY" \
    '.inbounds[0].settings.clients[0].id = $uuid
     | .inbounds[0].streamSettings.realitySettings.serverNames = [$sni]
     | .inbounds[0].streamSettings.realitySettings.dest = ($sni + ":443")
     | .inbounds[0].streamSettings.realitySettings.shortIds = [$sid]
     | .inbounds[0].streamSettings.realitySettings.privateKey = $pk' \
    "$CONFIG_FILE" > "$TMP"
  # Write into the existing inode (not `mv`) to preserve the host file's owner/permissions.
  cat "$TMP" > "$CONFIG_FILE"
  rm -f "$TMP"

  cat > "$IDENTITY_FILE" <<EOF
UUID=$UUID
SHORT_ID=$SHORT_ID
SNI_DOMAIN=$SNI_DOMAIN
PUBLIC_KEY=$PUBLIC_KEY
EOF

  echo "Generated new Xray identity: uuid=$UUID shortId=$SHORT_ID sni=$SNI_DOMAIN publicKey=$PUBLIC_KEY"
fi

# The portal's TLS material is independent of the Xray identity above, so it's checked and
# generated separately — deleting identity.env to rotate the VLESS identity shouldn't also
# force a new (self-signed) TLS certificate, and vice versa.
mkdir -p "$TLS_DIR"
if [ -f "$TLS_CERT" ] && [ -f "$TLS_KEY" ]; then
  echo "TLS certificate already present, keeping existing cert."
else
  openssl req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
    -keyout "$TLS_KEY" -out "$TLS_CERT" \
    -subj "/CN=xray-portal" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
  echo "Generated self-signed TLS certificate for the portal (until a DOMAIN is configured for Let's Encrypt)."
fi
