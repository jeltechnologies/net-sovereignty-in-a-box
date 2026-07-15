FROM alpine:3.20
RUN apk add --no-cache jq openssl
COPY generate-identity.sh /generate-identity.sh
RUN chmod +x /generate-identity.sh
ENTRYPOINT ["/generate-identity.sh"]
