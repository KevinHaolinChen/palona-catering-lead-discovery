package com.palona.cateringleads.model;

public record OutreachResponse(
        String prospectId,
        String generator,
        String text,
        String note
) {}
