package com.jeltechnologies.portal.security;

import com.jeltechnologies.portal.config.PortalProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Same credential check either flow (native Basic Auth header or the login-page cookie)
 * ends up using, so both {@link PortalAuthFilter} and the login form validate identically.
 */
@Component
public class PortalCredentials {

    private final PortalProperties properties;

    public PortalCredentials(PortalProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(String user, String pass) {
        return user != null && pass != null
                && safeEqual(user, properties.portalUserName())
                && safeEqual(pass, properties.portalPassword());
    }

    /** Validates a raw {@code user:pass} value, base64-encoded exactly like a Basic Auth header. */
    public boolean isValidBase64(String base64Value) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int idx = decoded.indexOf(':');
        if (idx == -1) {
            return false;
        }
        return isValid(decoded.substring(0, idx), decoded.substring(idx + 1));
    }

    /** Encodes credentials the same way a browser would for a Basic Auth header. */
    public String encodeBase64(String user, String pass) {
        return Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean safeEqual(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
