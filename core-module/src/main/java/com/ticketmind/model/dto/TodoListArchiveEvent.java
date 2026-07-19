package com.ticketmind.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TodoListArchiveEvent(
        String taskId,
        List<TodoItem> items,
        OffsetDateTime completedAt
) {
}
