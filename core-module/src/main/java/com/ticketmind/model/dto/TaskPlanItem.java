package com.ticketmind.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TaskPlanItem(
        String taskCode,
        String title,
        String description,
        TaskAssignee assignee,
        List<String> dependencies,
        TaskExecutionStatus status,
        boolean predefined,
        String result,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
