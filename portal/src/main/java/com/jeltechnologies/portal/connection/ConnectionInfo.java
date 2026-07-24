package com.jeltechnologies.portal.connection;

public record ConnectionInfo(
        String protocol,
        String uuid,
        String address,
        int port,
        String flow,
        String security,
        String sni,
        String publicKey,
        String shortId,
        String fingerprint,
        String network,
        String location,
        String vlessLink) {
}
