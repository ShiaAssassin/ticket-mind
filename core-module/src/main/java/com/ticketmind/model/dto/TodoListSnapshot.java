package com.ticketmind.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TodoListSnapshot(
        String taskId,
        List<TodoItem> items,
        OffsetDateTime updatedAt
) {
}
