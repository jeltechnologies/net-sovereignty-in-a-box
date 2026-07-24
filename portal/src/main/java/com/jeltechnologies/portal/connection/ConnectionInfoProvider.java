package com.jeltechnologies.portal.connection;

import com.jeltechnologies.portal.config.PortalProperties;
import com.jeltechnologies.portal.identity.Identity;
import com.jeltechnologies.portal.identity.IdentityLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Resolved once at startup — the public IP and the derived VLESS link don't change
 * for the lifetime of the process, mirroring the old Node portal's one-shot resolution.
 */
@Component
public class ConnectionInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(ConnectionInfoProvider.class);

    private final ConnectionInfo connectionInfo;

    public ConnectionInfoProvider(IdentityLoader identityLoader, PublicIpClient publicIpClient,
                                   PortalProperties properties) throws IOException, InterruptedException {
        Identity identity = identityLoader.load();
        String publicIp = publicIpClient.fetchPublicIp();
        String location = publicIpClient.fetchLocation(publicIp);
        int port = Integer.parseInt(properties.xrayPort());

        String vlessLink = "vless://" + identity.uuid() + "@" + publicIp + ":" + port
                + "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=" + identity.sniDomain()
                + "&fp=chrome&pbk=" + identity.publicKey() + "&sid=" + identity.shortId()
                + "&type=tcp#" + encodeURIComponent(location);

        this.connectionInfo = new ConnectionInfo(
                "vless", identity.uuid(), publicIp, port, "xtls-rprx-vision", "reality",
                identity.sniDomain(), identity.publicKey(), identity.shortId(), "chrome", "tcp",
                location, vlessLink);

        log.info("Public IP: {}", publicIp);
        log.info("VLESS link: {}", vlessLink);
    }

    public ConnectionInfo connectionInfo() {
        return connectionInfo;
    }

    /**
     * Reproduces JS {@code encodeURIComponent}, which leaves {@code ! ' ( ) ~} unescaped and
     * encodes space as {@code %20} — both differ from {@link URLEncoder}'s form-encoding rules.
     */
    private static String encodeURIComponent(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }
}
