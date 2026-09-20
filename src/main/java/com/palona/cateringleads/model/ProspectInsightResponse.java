package com.palona.cateringleads.model;

import java.util.List;

public record ProspectInsightResponse(
        String prospectId,
        String generator,
        boolean generativeAi,
        List<String> reasons,
        String outreachSubject,
        String outreachBody,
        String evidenceNote
) {}
