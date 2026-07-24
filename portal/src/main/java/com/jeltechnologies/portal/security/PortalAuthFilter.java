package com.jeltechnologies.portal.security;

import com.jeltechnologies.portal.config.PortalProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class PortalAuthFilter extends HttpFilter {

    private final PortalProperties properties;

    public PortalAuthFilter(PortalProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (!properties.authConfigured()) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("text/plain");
            res.getWriter().write("Portal authentication is not configured. Set PORTAL_USER_NAME and "
                    + "PORTAL_PASSWORD in docker-compose.yaml and restart the stack.");
            return;
        }

        if (!isAuthorized(req)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setHeader("WWW-Authenticate", "Basic realm=\"Xray Portal\", charset=\"UTF-8\"");
            res.setContentType("text/plain");
            res.getWriter().write("Authentication required");
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isAuthorized(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int idx = decoded.indexOf(':');
        if (idx == -1) {
            return false;
        }
        String user = decoded.substring(0, idx);
        String pass = decoded.substring(idx + 1);
        return safeEqual(user, properties.portalUserName()) && safeEqual(pass, properties.portalPassword());
    }

    private static boolean safeEqual(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
