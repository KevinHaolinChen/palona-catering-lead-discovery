package com.palona.cateringleads.model;

import java.util.List;
import java.util.Map;

public record CampaignDashboardResponse(
        int totalProspects,
        int newCount,
        int contactedCount,
        int followUpCount,
        int repliedCount,
        int wonCount,
        int lostCount,
        int dueTodayCount,
        List<String> dueProspectIds,
        Map<String, Integer> learnedCategoryAdjustments
) {}
