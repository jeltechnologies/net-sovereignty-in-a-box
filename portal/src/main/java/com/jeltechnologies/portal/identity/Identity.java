package com.jeltechnologies.portal.identity;

public record Identity(String uuid, String shortId, String sniDomain, String publicKey) {
}
