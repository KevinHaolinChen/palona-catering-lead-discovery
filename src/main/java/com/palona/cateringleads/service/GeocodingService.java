package com.palona.cateringleads.service;

import com.palona.cateringleads.model.GeocodeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeocodingService {

    private static final String USER_AGENT =
            "VerityScout/0.3 (+https://github.com/KevinHaolinChen/verityscout)";
    private static final long MIN_REQUEST_INTERVAL_MS = 1_100L;

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, GeocodeResponse> cache = new ConcurrentHashMap<>();
    private final Object rateLock = new Object();
    private long lastRequestAtMs;

    public GeocodingService(
            JsonMapper jsonMapper,
            @Value("${verityscout.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl
    ) {
        this.jsonMapper = jsonMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public GeocodeResponse geocode(String rawAddress) {
        String address = rawAddress == null ? "" : rawAddress.trim();
        if (address.length() < 5 || address.length() > 300) {
            throw new IllegalArgumentException("Enter a complete restaurant address.");
        }

        String key = address.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        GeocodeResponse cached = cache.get(key);
        if (cached != null) return cached;

        synchronized (rateLock) {
            cached = cache.get(key);
            if (cached != null) return cached;
            respectPublicRateLimit();

            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/search?format=jsonv2&limit=1&q=" + encoded);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                lastRequestAtMs = System.currentTimeMillis();
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Geocoding provider returned HTTP " + response.statusCode());
                }

                JsonNode root = jsonMapper.readTree(response.body());
                if (!root.isArray() || root.isEmpty()) {
                    throw new IllegalArgumentException("Restaurant address could not be found.");
                }

                JsonNode first = root.get(0);
                GeocodeResponse result = new GeocodeResponse(
                        address,
                        first.get("display_name").asText(address),
                        Double.parseDouble(first.get("lat").asText()),
                        Double.parseDouble(first.get("lon").asText()),
                        "OpenStreetMap Nominatim"
                );
                cache.put(key, result);
                return result;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Geocoding was interrupted", exception);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Geocoding provider is unavailable", exception);
            }
        }
    }

    private void respectPublicRateLimit() {
        long elapsed = System.currentTimeMillis() - lastRequestAtMs;
        long remaining = MIN_REQUEST_INTERVAL_MS - elapsed;
        if (remaining <= 0) return;
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Geocoding was interrupted", exception);
        }
    }
}
