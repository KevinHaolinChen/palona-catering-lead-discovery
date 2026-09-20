package com.palona.cateringleads.service;

import com.palona.cateringleads.model.GeocodeResponse;
import com.palona.cateringleads.model.RestaurantSearchResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeocodingService {

    private static final String USER_AGENT =
            "VerityScout/0.4 (+https://github.com/KevinHaolinChen/verityscout)";
    private static final long MIN_REQUEST_INTERVAL_MS = 1_100L;
    private static final Set<String> FOOD_TYPES = Set.of(
            "restaurant", "cafe", "fast_food", "food_court", "bar", "pub", "bakery"
    );

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, GeocodeResponse> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, List<RestaurantSearchResult>> searchCache = new ConcurrentHashMap<>();
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

    public List<RestaurantSearchResult> searchRestaurants(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2 || query.length() > 160) {
            throw new IllegalArgumentException("Enter at least 2 characters to search restaurants.");
        }

        String cacheKey = canonical(query);
        List<RestaurantSearchResult> cached = searchCache.get(cacheKey);
        if (cached != null) return cached;

        synchronized (rateLock) {
            cached = searchCache.get(cacheKey);
            if (cached != null) return cached;
            respectPublicRateLimit();

            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl
                    + "/search?format=jsonv2&limit=12&addressdetails=1&namedetails=1&extratags=1&q="
                    + encoded);

            JsonNode root = executeJson(uri);
            List<RestaurantSearchResult> all = new ArrayList<>();
            for (JsonNode item : root) {
                String displayName = text(item, "display_name");
                String type = text(item, "type");
                String category = text(item, "category");
                String name = text(item, "name");

                JsonNode namedetails = item.get("namedetails");
                if ((name == null || name.isBlank()) && namedetails != null) {
                    name = text(namedetails, "name");
                }
                if (name == null || name.isBlank()) {
                    name = displayName == null ? query : displayName.split(",")[0].trim();
                }

                String lat = text(item, "lat");
                String lon = text(item, "lon");
                if (lat == null || lon == null || displayName == null) continue;

                String label = type != null ? type : category;
                all.add(new RestaurantSearchResult(
                        name,
                        displayName,
                        Double.parseDouble(lat),
                        Double.parseDouble(lon),
                        label == null ? "place" : label,
                        "OpenStreetMap Nominatim"
                ));
            }

            List<RestaurantSearchResult> foodMatches = all.stream()
                    .filter(item -> FOOD_TYPES.contains(item.category()))
                    .toList();
            List<RestaurantSearchResult> results = foodMatches.isEmpty() ? all : foodMatches;
            results = results.stream().limit(8).toList();
            searchCache.put(cacheKey, results);
            return results;
        }
    }

    public GeocodeResponse geocode(String rawAddress) {
        String address = rawAddress == null ? "" : rawAddress.trim();
        if (address.length() < 5 || address.length() > 300) {
            throw new IllegalArgumentException("Enter a complete restaurant address.");
        }

        String key = canonical(address);
        GeocodeResponse cached = geocodeCache.get(key);
        if (cached != null) return cached;

        synchronized (rateLock) {
            cached = geocodeCache.get(key);
            if (cached != null) return cached;
            respectPublicRateLimit();

            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/search?format=jsonv2&limit=1&q=" + encoded);
            JsonNode root = executeJson(uri);
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
            geocodeCache.put(key, result);
            return result;
        }
    }

    private JsonNode executeJson(URI uri) {
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
                throw new IllegalStateException("Restaurant lookup provider returned HTTP " + response.statusCode());
            }
            return jsonMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Restaurant lookup was interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Restaurant lookup provider is unavailable", exception);
        }
    }

    private static String canonical(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void respectPublicRateLimit() {
        long elapsed = System.currentTimeMillis() - lastRequestAtMs;
        long remaining = MIN_REQUEST_INTERVAL_MS - elapsed;
        if (remaining <= 0) return;
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Restaurant lookup was interrupted", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }
}
