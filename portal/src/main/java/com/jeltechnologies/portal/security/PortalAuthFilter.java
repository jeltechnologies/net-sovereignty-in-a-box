package com.jeltechnologies.portal.security;

import com.jeltechnologies.portal.config.AcmeProperties;
import com.jeltechnologies.portal.config.PortalProperties;
import com.jeltechnologies.portal.tls.AcmeChallengeController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PortalAuthFilter extends HttpFilter {

    public static final String AUTH_COOKIE_NAME = "portal_auth";
    private static final String LOGIN_PATH = "/login";

    private final PortalProperties properties;
    private final AcmeProperties acmeProperties;
    private final PortalCredentials credentials;

    public PortalAuthFilter(PortalProperties properties, AcmeProperties acmeProperties,
                             PortalCredentials credentials) {
        this.properties = properties;
        this.acmeProperties = acmeProperties;
        this.credentials = credentials;
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // The ACME HTTP-01 challenge must be reachable without auth, on any connector.
        if (req.getRequestURI().startsWith(AcmeChallengeController.CHALLENGE_PATH_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        // The plain-HTTP connector exists only to answer ACME challenges — never let it serve
        // real portal content or accept credentials over cleartext HTTP.
        if (req.getLocalPort() == acmeProperties.httpPort()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (!properties.authConfigured()) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("text/plain");
            res.getWriter().write("Portal authentication is not configured. Set PORTAL_USER_NAME and "
                    + "PORTAL_PASSWORD in docker-compose.yaml and restart the stack.");
            return;
        }

        // The login page (and its form submission) must stay reachable while unauthenticated —
        // it's the only way to become authenticated in the first place.
        if (LOGIN_PATH.equals(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Basic ")) {
            // Presented explicitly (curl -u, scripts) — honor real Basic Auth semantics here,
            // no redirect to the HTML login page, so existing scripted/API usage keeps working.
            if (credentials.isValidBase64(header.substring(6))) {
                chain.doFilter(req, res);
            } else {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("text/plain");
                res.getWriter().write("Authentication required");
            }
            return;
        }

        String cookieValue = readCookie(req);
        if (cookieValue != null && credentials.isValidBase64(cookieValue)) {
            chain.doFilter(req, res);
            return;
        }

        // No native WWW-Authenticate challenge here on purpose — that's what triggers the
        // browser's own ugly login popup. Send browsers to our own login page instead.
        res.sendRedirect(LOGIN_PATH);
    }

    private static String readCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
