package com.palona.cateringleads.service;

import com.palona.cateringleads.model.PageSnapshot;
import com.palona.cateringleads.model.ProspectInsightResponse;
import com.palona.cateringleads.model.ProspectOutreachResponse;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProspectOutreachService {

    private static final String USER_AGENT =
            "Gather/0.7 (+https://github.com/KevinHaolinChen/verityscout)";

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63})\\b"
    );
    private static final Pattern HREF = Pattern.compile(
            "(?i)href\\s*=\\s*[\"']([^\"']+)[\"']"
    );

    private static final List<String> COMMON_CONTACT_PATHS = List.of(
            "/contact", "/contact-us", "/about", "/about-us"
    );
    private static final List<String> PREFERRED_PREFIXES = List.of(
            "catering", "events", "office", "admin", "hello", "info", "contact", "sales"
    );

    private final DiscoveredProspectRepository prospectRepository;
    private final EnrichmentService enrichmentService;
    private final ProspectIntelligenceService intelligenceService;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public ProspectOutreachService(
            DiscoveredProspectRepository prospectRepository,
            EnrichmentService enrichmentService,
            ProspectIntelligenceService intelligenceService,
            JsonMapper jsonMapper
    ) {
        this.prospectRepository = prospectRepository;
        this.enrichmentService = enrichmentService;
        this.intelligenceService = intelligenceService;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ProspectOutreachResponse prepare(String prospectId) {
        DiscoveredProspectEntity prospect = prospectRepository.findById(prospectId)
                .orElseThrow(() -> new IllegalArgumentException("Prospect not found: " + prospectId));

        ContactSeed seed = contactSeedFromOsm(prospect.getSourceUrl());
        String website = firstNonBlank(prospect.getWebsite(), seed.website());

        EmailResult emailResult = seed.email() == null
                ? discoverPublicEmail(website)
                : new EmailResult(seed.email(), seed.sourceUrl());

        ProspectInsightResponse insight = intelligenceService.analyze(prospectId);

        String note;
        if (emailResult.email() != null) {
            note = "Public email found from " + emailResult.sourceUrl() + ".";
        } else if (website == null || website.isBlank()) {
            note = "No public website or email was available in the discovery source. Gather did not invent a contact.";
        } else {
            note = "No public email was found in source tags, the official site, or reachable contact pages. Gather did not invent one.";
        }

        return new ProspectOutreachResponse(
                prospect.getId(),
                emailResult.email(),
                emailResult.sourceUrl(),
                insight.generator(),
                insight.generativeAi(),
                insight.outreachSubject(),
                insight.outreachBody(),
                note
        );
    }

    private ContactSeed contactSeedFromOsm(String sourceUrl) {
        URI apiUri = osmApiUri(sourceUrl);
        if (apiUri == null) return new ContactSeed(null, null, null);

        try {
            HttpRequest request = HttpRequest.newBuilder(apiUri)
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ContactSeed(null, null, null);
            }

            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode elements = root.get("elements");
            if (elements == null || !elements.isArray() || elements.isEmpty()) {
                return new ContactSeed(null, null, null);
            }

            JsonNode tags = elements.get(0).get("tags");
            if (tags == null || !tags.isObject()) return new ContactSeed(null, null, null);

            String email = firstNonBlank(text(tags, "contact:email"), text(tags, "email"));
            String website = firstNonBlank(text(tags, "contact:website"), text(tags, "website"));

            if (email != null) {
                Set<String> normalized = extractEmails(email);
                email = normalized.stream().findFirst().orElse(null);
            }

            return new ContactSeed(email, website, sourceUrl);
        } catch (Exception ignored) {
            return new ContactSeed(null, null, null);
        }
    }

    private EmailResult discoverPublicEmail(String rawWebsite) {
        URI base = normalizeWebsite(rawWebsite);
        if (base == null) return new EmailResult(null, null);

        List<EmailResult> matches = new ArrayList<>();
        LinkedHashSet<URI> pages = new LinkedHashSet<>();
        pages.add(base);

        PageSnapshot homepage = null;
        try {
            homepage = enrichmentService.fetchPublicPage(base.toString());
            collectEmails(matches, homepage);
            pages.addAll(discoverContactPages(base, homepage.html()));
        } catch (Exception ignored) {
            // Fixed contact paths below may still work.
        }

        for (String path : COMMON_CONTACT_PATHS) {
            try {
                pages.add(new URI(base.getScheme(), base.getAuthority(), path, null, null));
            } catch (Exception ignored) {
                // Skip malformed derived path.
            }
        }

        int checked = homepage == null ? 0 : 1;
        for (URI target : pages) {
            if (checked >= 6) break;
            if (homepage != null && sameUrl(homepage.url(), target.toString())) continue;

            try {
                PageSnapshot page = enrichmentService.fetchPublicPage(target.toString());
                collectEmails(matches, page);
            } catch (Exception ignored) {
                // Public sites commonly block automated requests.
            }
            checked++;
        }

        return matches.stream()
                .distinct()
                .min(Comparator.comparingInt(result -> emailPreference(result.email())))
                .orElse(new EmailResult(null, null));
    }

    private static void collectEmails(List<EmailResult> matches, PageSnapshot page) {
        Set<String> found = new LinkedHashSet<>();
        found.addAll(extractEmails(page.text()));
        found.addAll(extractEmails(decodeBasicEntities(page.html())));
        for (String email : found) {
            matches.add(new EmailResult(email, page.url()));
        }
    }

    static Set<String> extractEmails(String text) {
        Set<String> emails = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return emails;

        Matcher matcher = EMAIL.matcher(decodeBasicEntities(text));
        while (matcher.find()) {
            String email = matcher.group(1).toLowerCase(Locale.ROOT);
            if (looksUsable(email)) emails.add(email);
        }
        return emails;
    }

    private static Set<URI> discoverContactPages(URI base, String html) {
        Set<URI> links = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return links;

        Matcher matcher = HREF.matcher(html);
        while (matcher.find() && links.size() < 4) {
            String href = matcher.group(1).trim();
            String lower = href.toLowerCase(Locale.ROOT);
            if (!(lower.contains("contact")
                    || lower.contains("about")
                    || lower.contains("team")
                    || lower.contains("staff")
                    || lower.contains("directory"))) {
                continue;
            }

            try {
                URI resolved = base.resolve(href);
                if (resolved.getHost() != null
                        && resolved.getHost().equalsIgnoreCase(base.getHost())
                        && ("http".equalsIgnoreCase(resolved.getScheme())
                            || "https".equalsIgnoreCase(resolved.getScheme()))) {
                    links.add(new URI(
                            resolved.getScheme(),
                            resolved.getAuthority(),
                            resolved.getPath(),
                            resolved.getQuery(),
                            null
                    ));
                }
            } catch (Exception ignored) {
                // Ignore malformed links.
            }
        }
        return links;
    }

    private static URI osmApiUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return null;
        try {
            URI source = URI.create(sourceUrl);
            if (source.getHost() == null || !source.getHost().endsWith("openstreetmap.org")) return null;

            String[] parts = source.getPath().split("/");
            if (parts.length < 3) return null;
            String type = parts[1];
            String id = parts[2];
            if (!Set.of("node", "way", "relation").contains(type) || !id.matches("\\d+")) return null;

            return URI.create("https://api.openstreetmap.org/api/0.6/" + type + "/" + id + ".json");
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean looksUsable(String email) {
        String lower = email.toLowerCase(Locale.ROOT);
        return !lower.endsWith(".png")
                && !lower.endsWith(".jpg")
                && !lower.endsWith(".jpeg")
                && !lower.endsWith(".gif")
                && !lower.contains("example.com")
                && !lower.startsWith("noreply@")
                && !lower.startsWith("no-reply@");
    }

    private static int emailPreference(String email) {
        if (email == null) return Integer.MAX_VALUE;
        String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
        for (int i = 0; i < PREFERRED_PREFIXES.size(); i++) {
            if (local.contains(PREFERRED_PREFIXES.get(i))) return i;
        }
        return 100;
    }

    private static URI normalizeWebsite(String rawWebsite) {
        if (rawWebsite == null || rawWebsite.isBlank()) return null;
        try {
            String value = rawWebsite.trim();
            if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getHost().isBlank()) return null;
            return new URI(uri.getScheme(), uri.getAuthority(), "/", null, null);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String decodeBasicEntities(String value) {
        if (value == null) return "";
        return value
                .replace("&#64;", "@")
                .replace("&#x40;", "@")
                .replace("&commat;", "@")
                .replace("&#46;", ".")
                .replace("&#x2e;", ".");
    }

    private static boolean sameUrl(String left, String right) {
        return left != null && right != null
                && left.replaceAll("/+$", "").equalsIgnoreCase(right.replaceAll("/+$", ""));
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private record ContactSeed(String email, String website, String sourceUrl) {}
    private record EmailResult(String email, String sourceUrl) {}
}
