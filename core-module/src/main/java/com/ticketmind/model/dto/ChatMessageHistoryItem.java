package com.ticketmind.model.dto;

import com.ticketmind.model.entity.ChatMessageRole;

import java.time.Instant;

public record ChatMessageHistoryItem(
        Long id,
        ChatMessageRole role,
        String content,
        Instant createdAt
) {
}
