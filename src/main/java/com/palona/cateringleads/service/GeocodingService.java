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
            "VerityScout/0.5 (+https://github.com/KevinHaolinChen/verityscout)";
    private static final long NOMINATIM_MIN_REQUEST_INTERVAL_MS = 1_100L;
    private static final Set<String> FOOD_TYPES = Set.of(
            "restaurant", "cafe", "fast_food", "food_court", "bar", "pub", "bakery"
    );

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String nominatimBaseUrl;
    private final String photonBaseUrl;
    private final Map<String, GeocodeResponse> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, List<RestaurantSearchResult>> searchCache = new ConcurrentHashMap<>();
    private final Object rateLock = new Object();
    private long lastNominatimRequestAtMs;

    public GeocodingService(
            JsonMapper jsonMapper,
            @Value("${verityscout.geocoding.base-url:https://nominatim.openstreetmap.org}") String nominatimBaseUrl,
            @Value("${verityscout.search.photon-base-url:https://photon.komoot.io}") String photonBaseUrl
    ) {
        this.jsonMapper = jsonMapper;
        this.nominatimBaseUrl = nominatimBaseUrl.replaceAll("/+$", "");
        this.photonBaseUrl = photonBaseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<RestaurantSearchResult> searchRestaurants(String rawQuery, Double latitude, Double longitude) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2 || query.length() > 160) {
            throw new IllegalArgumentException("Enter at least 2 characters to search restaurants.");
        }

        String cacheKey = canonical(query)
                + "|" + rounded(latitude)
                + "|" + rounded(longitude);
        List<RestaurantSearchResult> cached = searchCache.get(cacheKey);
        if (cached != null) return cached;

        StringBuilder url = new StringBuilder(photonBaseUrl)
                .append("/api?q=")
                .append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&limit=10&zoom=14&location_bias_scale=0.1");

        if (latitude != null && longitude != null
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180) {
            url.append("&lat=").append(latitude).append("&lon=").append(longitude);
        }

        JsonNode root = executeJson(URI.create(url.toString()), "Restaurant search");
        JsonNode features = root.get("features");
        if (features == null || !features.isArray()) return List.of();

        List<RestaurantSearchResult> all = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode properties = feature.get("properties");
            JsonNode geometry = feature.get("geometry");
            JsonNode coordinates = geometry == null ? null : geometry.get("coordinates");
            if (properties == null || coordinates == null || coordinates.size() < 2) continue;

            String name = text(properties, "name");
            if (name == null || name.isBlank()) continue;

            String osmValue = text(properties, "osm_value");
            String osmKey = text(properties, "osm_key");
            String category = osmValue == null ? (osmKey == null ? "place" : osmKey) : osmValue;

            double lon = coordinates.get(0).asDouble();
            double lat = coordinates.get(1).asDouble();
            String address = buildAddress(properties);

            all.add(new RestaurantSearchResult(
                    name,
                    address,
                    lat,
                    lon,
                    category,
                    "OpenStreetMap via Photon"
            ));
        }

        List<RestaurantSearchResult> foodMatches = all.stream()
                .filter(item -> FOOD_TYPES.contains(item.category().toLowerCase(Locale.ROOT)))
                .toList();

        List<RestaurantSearchResult> results = (foodMatches.isEmpty() ? all : foodMatches)
                .stream()
                .limit(8)
                .toList();

        searchCache.put(cacheKey, results);
        return results;
    }

    /**
     * Retained for the explicit legacy address endpoint. Restaurant autocomplete uses Photon,
     * because public Nominatim does not permit client-side autocomplete.
     */
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
            respectNominatimRateLimit();

            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            URI uri = URI.create(nominatimBaseUrl + "/search?format=jsonv2&limit=1&q=" + encoded);
            JsonNode root = executeNominatimJson(uri);
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

    private JsonNode executeJson(URI uri, String label) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(label + " provider returned HTTP " + response.statusCode());
            }
            return jsonMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(label + " was interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(label + " provider is unavailable", exception);
        }
    }

    private JsonNode executeNominatimJson(URI uri) {
        try {
            JsonNode value = executeJson(uri, "Address lookup");
            lastNominatimRequestAtMs = System.currentTimeMillis();
            return value;
        } catch (RuntimeException exception) {
            lastNominatimRequestAtMs = System.currentTimeMillis();
            throw exception;
        }
    }

    private static String buildAddress(JsonNode properties) {
        String streetLine = joinNonBlank(text(properties, "housenumber"), text(properties, "street"));
        String locality = firstNonBlank(
                text(properties, "city"),
                firstNonBlank(text(properties, "locality"), text(properties, "district"))
        );
        String region = joinNonBlank(locality, text(properties, "state"), text(properties, "postcode"));
        String country = text(properties, "country");
        String value = joinWithComma(streetLine, region, country);
        return value.isBlank() ? "Address unavailable" : value;
    }

    private static String rounded(Double value) {
        return value == null ? "" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String canonical(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void respectNominatimRateLimit() {
        long elapsed = System.currentTimeMillis() - lastNominatimRequestAtMs;
        long remaining = NOMINATIM_MIN_REQUEST_INTERVAL_MS - elapsed;
        if (remaining <= 0) return;
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Address lookup was interrupted", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(part);
        }
        return result.toString();
    }

    private static String joinWithComma(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!result.isEmpty()) result.append(", ");
            result.append(part);
        }
        return result.toString();
    }
}
