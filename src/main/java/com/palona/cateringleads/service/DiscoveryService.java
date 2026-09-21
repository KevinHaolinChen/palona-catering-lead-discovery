package com.palona.cateringleads.service;

import com.palona.cateringleads.model.DiscoveryCandidate;
import com.palona.cateringleads.model.ScoreBreakdown;
import com.palona.cateringleads.model.SearchCriteria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Order(2)
public class DiscoveryService implements ProspectSource {

    private static final String USER_AGENT =
            "Gather/0.8 (+https://github.com/KevinHaolinChen/verityscout)";

    private final JsonMapper jsonMapper;
    private final ScoringService scoringService;
    private final HttpClient httpClient;
    private final List<URI> overpassUrls;

    public DiscoveryService(
            JsonMapper jsonMapper,
            ScoringService scoringService,
            @Value("${verityscout.discovery.overpass-urls:https://overpass.private.coffee/api/interpreter,https://overpass-api.de/api/interpreter,https://maps.mail.ru/osm/tools/overpass/api/interpreter}") String configuredUrls
    ) {
        this.jsonMapper = jsonMapper;
        this.scoringService = scoringService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.overpassUrls = List.of(configuredUrls.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(URI::create)
                .toList();
    }

    @Override
    public List<DiscoveryCandidate> discover(SearchCriteria criteria) {
        return discover(criteria.latitude(), criteria.longitude(), criteria.radiusMeters());
    }

    @Override
    public String sourceName() {
        return "OpenStreetMap via Overpass";
    }

    public List<DiscoveryCandidate> discover(double latitude, double longitude, int radiusMeters) {
        // Overpass is only a supplemental source. Cap its geographic query so a wide
        // 50-mile user search cannot turn into an expensive public-server query.
        int overpassRadiusMeters = Math.max(1, Math.min(radiusMeters, 20_000));

        String query = """
                [out:json][timeout:18];
                (
                  nwr(around:%d,%f,%f)[name][office];
                  nwr(around:%d,%f,%f)[name][amenity~"hospital|clinic|university|college|school|conference_centre|community_centre"];
                );
                out center tags qt;
                """.formatted(overpassRadiusMeters, latitude, longitude, overpassRadiusMeters, latitude, longitude);

        List<String> failures = new ArrayList<>();
        for (URI endpoint : overpassUrls) {
            try {
                HttpResponse<String> response = RetryExecutor.withBackoff(
                        2,
                        Duration.ofMillis(350),
                        () -> execute(endpoint, query)
                );
                return parseCandidates(jsonMapper.readTree(response.body()), latitude, longitude);
            } catch (Exception exception) {
                failures.add(endpoint.getHost() + ": " + rootMessage(exception));
            }
        }

        throw new IllegalStateException(
                "All discovery providers failed. " + String.join(" | ", failures)
                + ". Try again shortly or reduce the search radius."
        );
    }

    private HttpResponse<String> execute(URI endpoint, String query) {
        String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            if (response.body() == null || response.body().isBlank()) {
                throw new IllegalStateException("empty response");
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("request interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception.getClass().getSimpleName() + ": " + exception.getMessage(), exception);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message.replaceAll("\\s+", " ").trim();
    }

    private List<DiscoveryCandidate> parseCandidates(JsonNode root, double originLat, double originLon) {
        List<DiscoveryCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode elements = root.get("elements");
        if (elements == null || !elements.isArray()) return List.of();

        for (JsonNode element : elements) {
            JsonNode tags = element.get("tags");
            if (tags == null || !tags.isObject()) continue;

            String name = text(tags, "name");
            if (name == null || name.isBlank()) continue;

            Double lat = number(element, "lat");
            Double lon = number(element, "lon");
            JsonNode center = element.get("center");
            if (lat == null && center != null) lat = number(center, "lat");
            if (lon == null && center != null) lon = number(center, "lon");
            if (lat == null || lon == null) continue;

            String address = joinNonBlank(
                    text(tags, "addr:housenumber"),
                    text(tags, "addr:street"),
                    text(tags, "addr:city"),
                    text(tags, "addr:state"),
                    text(tags, "addr:postcode")
            );
            if (address.isBlank()) address = "Address not available from source";

            String dedupeKey = canonical(name) + "|" + canonical(address);
            if (!seen.add(dedupeKey)) continue;

            String category = classify(tags);
            String website = firstNonBlank(text(tags, "website"), text(tags, "contact:website"));
            String phone = firstNonBlank(text(tags, "phone"), text(tags, "contact:phone"));
            double distance = round2(distanceMiles(originLat, originLon, lat, lon));
            ScoreBreakdown breakdown = scoringService.heuristicScore(
                    category,
                    distance,
                    website != null && !website.isBlank(),
                    phone != null && !phone.isBlank()
            );
            String type = text(element, "type");
            String id = element.get("id") == null ? "" : element.get("id").asText();

            candidates.add(new DiscoveryCandidate(
                    name, category, address, website, phone, lat, lon, distance,
                    "https://www.openstreetmap.org/" + type + "/" + id,
                    sourceName(), breakdown.total(), breakdown
            ));
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(DiscoveryCandidate::score).reversed()
                        .thenComparingDouble(DiscoveryCandidate::distanceMiles))
                .limit(40)
                .toList();
    }

    static String canonical(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String classify(JsonNode tags) {
        String amenity = text(tags, "amenity");
        String office = text(tags, "office");
        if ("hospital".equals(amenity) || "clinic".equals(amenity)) return "hospital";
        if (Set.of("university", "college", "school").contains(amenity)) return "university_campus";
        if ("conference_centre".equals(amenity) || "community_centre".equals(amenity)) return "community_event_space";
        if (office != null && !office.isBlank()) return "corporate_office";
        return "organization";
    }

    private static double distanceMiles(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMiles = 3958.7613;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dPhi / 2), 2)
                + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(dLambda / 2), 2);
        return 2 * earthRadiusMiles * Math.asin(Math.sqrt(a));
    }

    private static double round2(double value) { return Math.round(value * 100.0) / 100.0; }
    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }
    private static Double number(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || !child.isNumber() ? null : child.asDouble();
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
}
