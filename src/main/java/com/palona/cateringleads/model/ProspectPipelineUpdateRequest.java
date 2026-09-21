package com.palona.cateringleads.model;

import java.time.Instant;

public record ProspectPipelineUpdateRequest(
        String stage,
        Instant nextFollowUpAt,
        String note
) {}
