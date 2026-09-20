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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Order(1)
public class PhotonDiscoverySource implements ProspectSource {

    private static final String USER_AGENT =
            "Gather/0.8 (+https://github.com/KevinHaolinChen/verityscout)";

    private final JsonMapper jsonMapper;
    private final ScoringService scoringService;
    private final HttpClient httpClient;
    private final String baseUrl;

    public PhotonDiscoverySource(
            JsonMapper jsonMapper,
            ScoringService scoringService,
            @Value("${verityscout.search.photon-base-url:https://photon.komoot.io}") String baseUrl
    ) {
        this.jsonMapper = jsonMapper;
        this.scoringService = scoringService;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<DiscoveryCandidate> discover(SearchCriteria criteria) {
        double radiusKm = Math.min(80.5, Math.max(0.1, criteria.radiusMeters() / 1000.0));

        List<QuerySpec> queries = List.of(
                new QuerySpec("corporate_office", "office"),
                new QuerySpec("hospital", "amenity:hospital"),
                new QuerySpec("university_campus", "amenity:university"),
                new QuerySpec("university_campus", "amenity:college"),
                new QuerySpec("university_campus", "amenity:school"),
                new QuerySpec("community_event_space", "amenity:community_centre"),
                new QuerySpec("community_event_space", "amenity:conference_centre")
        );

        List<CompletableFuture<List<DiscoveryCandidate>>> futures = queries.stream()
                .map(spec -> fetchCandidates(spec, criteria, radiusKm)
                        .completeOnTimeout(List.of(), 6, TimeUnit.SECONDS)
                        .exceptionally(ignored -> List.of()))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .completeOnTimeout(null, 7, TimeUnit.SECONDS)
                .join();

        List<DiscoveryCandidate> results = new ArrayList<>();
        for (CompletableFuture<List<DiscoveryCandidate>> future : futures) {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                results.addAll(future.join());
            }
        }
        return results;
    }

    private CompletableFuture<List<DiscoveryCandidate>> fetchCandidates(
            QuerySpec spec,
            SearchCriteria criteria,
            double radiusKm
    ) {
        String url = baseUrl
                + "/reverse?lon=" + criteria.longitude()
                + "&lat=" + criteria.latitude()
                + "&radius=" + radiusKm
                + "&limit=8&osm_tag="
                + URLEncoder.encode(spec.osmTag(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return List.<DiscoveryCandidate>of();
                    }
                    return parse(response.body(), spec.category(), criteria);
                });
    }

    private List<DiscoveryCandidate> parse(String body, String category, SearchCriteria criteria) {
        try {
            JsonNode root = jsonMapper.readTree(body);
            JsonNode features = root.get("features");
            if (features == null || !features.isArray()) return List.of();

            List<DiscoveryCandidate> results = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode properties = feature.get("properties");
                JsonNode geometry = feature.get("geometry");
                JsonNode coordinates = geometry == null ? null : geometry.get("coordinates");
                if (properties == null || coordinates == null || coordinates.size() < 2) continue;

                String name = text(properties, "name");
                if (name == null || name.isBlank()) continue;

                double lon = coordinates.get(0).asDouble();
                double lat = coordinates.get(1).asDouble();
                double distance = round2(distanceMiles(criteria.latitude(), criteria.longitude(), lat, lon));
                double allowedMiles = criteria.radiusMeters() / 1609.344;
                if (distance > allowedMiles) continue;

                String address = buildAddress(properties);
                JsonNode extra = properties.get("extra");
                String website = firstNonBlank(
                        text(extra, "website"),
                        text(extra, "contact:website")
                );
                String phone = firstNonBlank(
                        text(extra, "phone"),
                        text(extra, "contact:phone")
                );

                ScoreBreakdown score = scoringService.heuristicScore(
                        category,
                        distance,
                        website != null && !website.isBlank(),
                        phone != null && !phone.isBlank()
                );

                String osmType = text(properties, "osm_type");
                String osmId = text(properties, "osm_id");
                String sourceUrl = osmUrl(osmType, osmId);

                results.add(new DiscoveryCandidate(
                        name,
                        category,
                        address,
                        website,
                        phone,
                        distance,
                        sourceUrl,
                        sourceName(),
                        score.total(),
                        score
                ));
            }
            return results;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String buildAddress(JsonNode properties) {
        String streetLine = joinNonBlank(text(properties, "housenumber"), text(properties, "street"));
        String locality = firstNonBlank(text(properties, "city"), text(properties, "locality"));
        String regionLine = joinNonBlank(locality, text(properties, "state"), text(properties, "postcode"));
        String address = joinWithComma(streetLine, regionLine);
        return address.isBlank() ? "Address not available from source" : address;
    }

    private static String osmUrl(String type, String id) {
        if (type == null || id == null) return "https://www.openstreetmap.org/";
        String path = switch (type.toUpperCase(Locale.ROOT)) {
            case "N" -> "node";
            case "W" -> "way";
            case "R" -> "relation";
            default -> null;
        };
        return path == null ? "https://www.openstreetmap.org/" : "https://www.openstreetmap.org/" + path + "/" + id;
    }

    @Override
    public String sourceName() {
        return "OpenStreetMap via Photon";
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

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder value = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!value.isEmpty()) value.append(' ');
            value.append(part);
        }
        return value.toString();
    }

    private static String joinWithComma(String... parts) {
        StringBuilder value = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!value.isEmpty()) value.append(", ");
            value.append(part);
        }
        return value.toString();
    }

    private record QuerySpec(String category, String osmTag) {}
}
