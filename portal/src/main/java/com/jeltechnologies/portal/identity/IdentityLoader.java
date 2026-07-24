package com.jeltechnologies.portal.identity;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class IdentityLoader {

    private static final Path IDENTITY_FILE = Path.of("/etc/xray/identity.env");

    public Identity load() {
        Map<String, String> vars = new HashMap<>();
        try {
            for (String line : Files.readAllLines(IDENTITY_FILE)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx == -1) {
                    continue;
                }
                vars.put(trimmed.substring(0, idx), trimmed.substring(idx + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + IDENTITY_FILE, e);
        }

        Identity identity = new Identity(
                require(vars, "UUID"),
                require(vars, "SHORT_ID"),
                require(vars, "SNI_DOMAIN"),
                require(vars, "PUBLIC_KEY"));
        return identity;
    }

    private static String require(Map<String, String> vars, String key) {
        String value = vars.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "Missing " + key + " in " + IDENTITY_FILE + " — has the init container run yet?");
        }
        return value;
    }
}
