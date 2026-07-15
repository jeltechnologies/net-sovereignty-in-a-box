#!/bin/sh
set -eu

CONFIG_FILE=/etc/xray/config.json
TEMPLATE_FILE=/config.template.json
IDENTITY_FILE=/etc/xray/identity.env

if [ -f "$IDENTITY_FILE" ]; then
  echo "identity.env already present, keeping existing identity."
  exit 0
fi

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
