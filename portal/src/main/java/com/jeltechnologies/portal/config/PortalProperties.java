package com.jeltechnologies.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public record PortalProperties(String xrayPort, String portalUserName, String portalPassword, String domain) {

    public boolean authConfigured() {
        return portalUserName != null && !portalUserName.isEmpty()
                && portalPassword != null && !portalPassword.isEmpty();
    }

    public boolean domainConfigured() {
        return domain != null && !domain.isEmpty();
    }
}
