package com.ticketmind.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TaskPlanSnapshot(
        String planId,
        String taskType,
        String userMessage,
        String summary,
        TaskPlanStatus status,
        List<TaskPlanItem> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
