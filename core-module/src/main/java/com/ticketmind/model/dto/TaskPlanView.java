package com.ticketmind.model.dto;

import java.util.List;

public record TaskPlanView(
        String planId,
        String taskType,
        String summary,
        String status,
        int progress,
        int totalTasks,
        int completedTasks,
        List<String> steps,
        TaskPlanViewItem currentTask,
        List<TaskPlanViewItem> items
) {
}
