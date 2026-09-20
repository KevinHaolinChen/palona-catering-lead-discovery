package com.palona.cateringleads.model;

public record ProspectOutreachResponse(
        String prospectId,
        String email,
        String emailSourceUrl,
        String subject,
        String body,
        String note
) {}
