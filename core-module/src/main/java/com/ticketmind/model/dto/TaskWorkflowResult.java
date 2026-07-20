package com.ticketmind.model.dto;

public record TaskWorkflowResult(
        String answer,
        TaskPlanSnapshot plan,
        TaskPlanStatus finalStatus
) {
}
