package com.palona.cateringleads.service;

import com.palona.cateringleads.model.PageSnapshot;
import com.palona.cateringleads.model.ProspectOutreachResponse;
import com.palona.cateringleads.persistence.CampaignEntity;
import com.palona.cateringleads.persistence.CampaignRepository;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.persistence.DiscoveryRunEntity;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
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

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63})\\b"
    );
    private static final List<String> COMMON_CONTACT_PATHS = List.of("", "/contact", "/contact-us");
    private static final List<String> PREFERRED_PREFIXES = List.of(
            "catering", "events", "office", "admin", "hello", "info", "contact", "sales"
    );

    private final DiscoveredProspectRepository prospectRepository;
    private final DiscoveryRunRepository runRepository;
    private final CampaignRepository campaignRepository;
    private final EnrichmentService enrichmentService;

    public ProspectOutreachService(
            DiscoveredProspectRepository prospectRepository,
            DiscoveryRunRepository runRepository,
            CampaignRepository campaignRepository,
            EnrichmentService enrichmentService
    ) {
        this.prospectRepository = prospectRepository;
        this.runRepository = runRepository;
        this.campaignRepository = campaignRepository;
        this.enrichmentService = enrichmentService;
    }

    public ProspectOutreachResponse prepare(String prospectId) {
        DiscoveredProspectEntity prospect = prospectRepository.findById(prospectId)
                .orElseThrow(() -> new IllegalArgumentException("Prospect not found: " + prospectId));

        DiscoveryRunEntity run = runRepository.findById(prospect.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("Discovery run not found: " + prospect.getRunId()));

        CampaignEntity campaign = campaignRepository.findById(run.getCampaignId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + run.getCampaignId()));

        EmailResult emailResult = discoverPublicEmail(prospect.getWebsite());

        String subject = "Local catering / group-order options from " + campaign.getName();
        String body = outreachBody(campaign, prospect);

        String note = emailResult.email() == null
                ? "No public email was found on the organization's website or common contact pages. VerityScout will not invent one."
                : "Public email found on the organization's website.";

        return new ProspectOutreachResponse(
                prospect.getId(),
                emailResult.email(),
                emailResult.sourceUrl(),
                subject,
                body,
                note
        );
    }

    private EmailResult discoverPublicEmail(String rawWebsite) {
        URI base = normalizeWebsite(rawWebsite);
        if (base == null) return new EmailResult(null, null);

        List<EmailResult> matches = new ArrayList<>();
        for (String path : COMMON_CONTACT_PATHS) {
            try {
                URI target = path.isBlank()
                        ? base
                        : new URI(base.getScheme(), base.getAuthority(), path, null, null);

                PageSnapshot page = enrichmentService.fetchPublicPage(target.toString());
                for (String email : extractEmails(page.text())) {
                    matches.add(new EmailResult(email, page.url()));
                }
            } catch (Exception ignored) {
                // Public sites often block automated fetches; another contact path may still work.
            }
        }

        return matches.stream()
                .distinct()
                .min(Comparator.comparingInt(result -> emailPreference(result.email())))
                .orElse(new EmailResult(null, null));
    }

    static Set<String> extractEmails(String text) {
        Set<String> emails = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return emails;

        Matcher matcher = EMAIL.matcher(text);
        while (matcher.find()) {
            String email = matcher.group(1).toLowerCase(Locale.ROOT);
            if (looksUsable(email)) emails.add(email);
        }
        return emails;
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

    private static String outreachBody(CampaignEntity campaign, DiscoveredProspectEntity prospect) {
        String useCase = switch (prospect.getCategory()) {
            case "corporate_office", "corporate_hq", "corporate_campus" ->
                    "team meetings, employee meals, or office events";
            case "university_campus" ->
                    "department meetings, trainings, or campus events";
            case "hospital" ->
                    "staff meetings or trainings where outside catering is permitted";
            case "community_event_space" ->
                    "meetings, programs, or group events";
            default ->
                    "meetings, events, or group meals";
        };

        return """
                Hi %s team,

                I'm reaching out from %s. You're about %.1f miles from our location, and I thought we could be a convenient option for %s.

                We offer group-order and catering options, and I'd be happy to send over a concise menu, pricing, and delivery details if that would be useful.

                Who would be the best person to speak with about group food orders?

                Best,
                %s
                """.formatted(
                prospect.getOrganization(),
                campaign.getName(),
                prospect.getDistanceMiles(),
                useCase,
                campaign.getName()
        ).trim();
    }

    private record EmailResult(String email, String sourceUrl) {}
}
