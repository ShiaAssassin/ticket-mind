package com.ticketmind.model.dto;

import java.util.List;

public record TaskPlanViewItem(
        String taskCode,
        String title,
        String description,
        String assignee,
        List<String> dependencyCodes,
        List<String> dependencyTitles,
        String status,
        String result,
        boolean predefined
) {
}
