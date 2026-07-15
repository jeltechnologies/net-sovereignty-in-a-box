FROM teddysun/xray:latest
RUN apk add --no-cache jq openssl
COPY config.template.json /config.template.json
COPY generate-identity.sh /generate-identity.sh
RUN chmod +x /generate-identity.sh
ENTRYPOINT ["/generate-identity.sh"]
