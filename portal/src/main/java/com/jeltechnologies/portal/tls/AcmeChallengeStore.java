package com.jeltechnologies.portal.tls;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the HTTP-01 challenge responses currently expected by Let's Encrypt, keyed by token.
 * Populated by {@link AcmeCertificateService} while an order is in flight and read by
 * {@link AcmeChallengeController} on the plain-HTTP connector.
 */
@Component
public class AcmeChallengeStore {

    private final ConcurrentHashMap<String, String> authorizations = new ConcurrentHashMap<>();

    public void put(String token, String keyAuthorization) {
        authorizations.put(token, keyAuthorization);
    }

    public String get(String token) {
        return authorizations.get(token);
    }

    public void remove(String token) {
        authorizations.remove(token);
    }
}
