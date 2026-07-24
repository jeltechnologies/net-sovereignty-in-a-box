package com.jeltechnologies.portal.connection;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class PublicIpClient {

    private static final Logger log = LoggerFactory.getLogger(PublicIpClient.class);
    private static final String FALLBACK_LABEL = "Xray-REALITY";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchPublicIp() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.ipify.org"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("ipify returned status " + response.statusCode());
        }
        String ip = response.body().trim();
        if (ip.isEmpty()) {
            throw new IOException("ipify returned an empty response");
        }
        return ip;
    }

    public String fetchLocation(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://ip-api.com/json/" + ip + "?fields=status,country,city"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("ip-api.com returned status " + response.statusCode());
            }
            JsonNode data = objectMapper.readTree(response.body());
            String status = data.path("status").asText("");
            String country = data.path("country").asText("");
            if (!"success".equals(status) || country.isEmpty()) {
                throw new IOException("ip-api.com lookup failed");
            }
            String city = data.path("city").asText("");
            return city.isEmpty() ? country : city + ", " + country;
        } catch (Exception e) {
            log.warn("Location lookup failed, falling back to default label: {}", e.getMessage());
            return FALLBACK_LABEL;
        }
    }
}
