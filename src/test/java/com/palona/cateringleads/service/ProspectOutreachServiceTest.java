package com.palona.cateringleads.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProspectOutreachServiceTest {

    @Test
    void extractsPublicEmailsWithoutInventingContacts() {
        var emails = ProspectOutreachService.extractEmails(
                "Contact catering@example.org or info@company.com. Ignore no-reply@company.com."
        );

        assertThat(emails)
                .contains("catering@example.org", "info@company.com")
                .doesNotContain("no-reply@company.com");
    }
}
