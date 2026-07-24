package com.jeltechnologies.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme")
public record AcmeProperties(int httpPort, String directoryUrl) {
}
