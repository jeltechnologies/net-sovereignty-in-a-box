FROM teddysun/xray:latest
RUN apk add --no-cache jq openssl
COPY generate-identity.sh /generate-identity.sh
RUN chmod +x /generate-identity.sh
ENTRYPOINT ["/generate-identity.sh"]
