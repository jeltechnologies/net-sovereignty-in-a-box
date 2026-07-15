#!/bin/sh
set -eu

CONFIG_FILE=/etc/xray/config.json
TEMPLATE_FILE=/etc/xray/config.template.json
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

PICK=$(( $(od -An -N1 -tu1 /dev/urandom | tr -d ' ') % 2 ))
if [ "$PICK" -eq 0 ]; then
  SNI_DOMAIN=www.cloudflare.com
else
  SNI_DOMAIN=www.microsoft.com
fi

TMP=$(mktemp)
jq \
  --arg uuid "$UUID" \
  --arg sid "$SHORT_ID" \
  --arg sni "$SNI_DOMAIN" \
  '.inbounds[0].settings.clients[0].id = $uuid
   | .inbounds[0].streamSettings.realitySettings.serverNames = [$sni]
   | .inbounds[0].streamSettings.realitySettings.dest = ($sni + ":443")
   | .inbounds[0].streamSettings.realitySettings.shortIds = [$sid]' \
  "$CONFIG_FILE" > "$TMP"
# Write into the existing inode (not `mv`) to preserve the host file's owner/permissions.
cat "$TMP" > "$CONFIG_FILE"
rm -f "$TMP"

cat > "$IDENTITY_FILE" <<EOF
UUID=$UUID
SHORT_ID=$SHORT_ID
SNI_DOMAIN=$SNI_DOMAIN
EOF

echo "Generated new Xray identity: uuid=$UUID shortId=$SHORT_ID sni=$SNI_DOMAIN"
