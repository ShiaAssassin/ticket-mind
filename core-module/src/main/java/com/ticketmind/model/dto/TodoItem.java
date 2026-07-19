package com.ticketmind.model.dto;

import java.time.OffsetDateTime;

public record TodoItem(
        String id,
        String content,
        TodoStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
