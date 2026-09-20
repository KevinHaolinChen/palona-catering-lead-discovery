package com.palona.cateringleads.service;

import com.palona.cateringleads.model.PageSnapshot;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class EnrichmentService {

    private static final String USER_AGENT = "Gather/0.7 (restaurant prospect intelligence; https://github.com/KevinHaolinChen/verityscout)";
    private static final int MAX_HTML_BYTES = 500_000;
    private static final int MAX_TEXT_CHARS = 20_000;
    private static final int MAX_REDIRECTS = 3;

    private static final Pattern SCRIPT = Pattern.compile("<script[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE = Pattern.compile("<style[\\s\\S]*?</style>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public PageSnapshot fetchPublicPage(String url) {
        return fetch(validatePublicUri(URI.create(url)), MAX_REDIRECTS);
    }

    private PageSnapshot fetch(URI uri, int redirectsRemaining) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                if (redirectsRemaining == 0) throw new IllegalStateException("Too many redirects");
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalStateException("Redirect without location"));
                return fetch(validatePublicUri(uri.resolve(location)), redirectsRemaining - 1);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Source returned HTTP " + response.statusCode());
            }
            String contentType = response.headers().firstValue("content-type")
                    .orElse("text/plain").toLowerCase(Locale.ROOT);
            if (!(contentType.contains("text/html") || contentType.contains("text/plain"))) {
                throw new IllegalStateException("Unsupported source content type: " + contentType);
            }
            if (response.body().length > MAX_HTML_BYTES) {
                throw new IllegalStateException("Source body exceeds safe size limit");
            }

            String html = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            String visibleText = visibleText(html);
            if (visibleText.length() > MAX_TEXT_CHARS) visibleText = visibleText.substring(0, MAX_TEXT_CHARS);
            return new PageSnapshot(uri.toString(), response.statusCode(), visibleText, html);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Public-page fetch was interrupted", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fetch public page", exception);
        }
    }

    static URI validatePublicUri(URI uri) {
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only public HTTP(S) URLs are supported");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL must include a public host");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Private or local network destinations are not allowed");
                }
            }
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("Unable to resolve source host", exception);
        }
        return uri;
    }

    static String visibleText(String html) {
        String withoutScripts = SCRIPT.matcher(html).replaceAll(" ");
        String withoutStyles = STYLE.matcher(withoutScripts).replaceAll(" ");
        String withoutTags = TAG.matcher(withoutStyles).replaceAll(" ");
        return WHITESPACE.matcher(withoutTags).replaceAll(" ").trim();
    }
}
