package com.palona.cateringleads.service;

import com.palona.cateringleads.model.PageSnapshot;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

@Service
public class EnrichmentService {

    private static final String USER_AGENT = "palona-catering-lead-demo/1.0 (take-home project)";
    private static final int MAX_HTML_CHARS = 500_000;
    private static final int MAX_TEXT_CHARS = 20_000;

    private static final Pattern SCRIPT = Pattern.compile("<script[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE = Pattern.compile("<style[\\s\\S]*?</style>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Fetches a single direct public page with conservative time and size bounds.
     * This is intentionally a verifier/enricher, not a general-purpose crawler.
     */
    public PageSnapshot fetchPublicPage(String url) {
        URI uri = URI.create(url);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Only public HTTP(S) URLs are supported");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Source returned HTTP " + response.statusCode());
            }
            String body = response.body();
            if (body.length() > MAX_HTML_CHARS) {
                body = body.substring(0, MAX_HTML_CHARS);
            }
            String visibleText = visibleText(body);
            if (visibleText.length() > MAX_TEXT_CHARS) {
                visibleText = visibleText.substring(0, MAX_TEXT_CHARS);
            }
            return new PageSnapshot(response.uri().toString(), response.statusCode(), visibleText);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Public-page fetch was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fetch public page", exception);
        }
    }

    static String visibleText(String html) {
        String withoutScripts = SCRIPT.matcher(html).replaceAll(" ");
        String withoutStyles = STYLE.matcher(withoutScripts).replaceAll(" ");
        String withoutTags = TAG.matcher(withoutStyles).replaceAll(" ");
        return WHITESPACE.matcher(withoutTags).replaceAll(" ").trim();
    }
}
