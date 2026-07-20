package com.ticketmind.agent.memory;

import java.time.Instant;

public record SystemPromptMemory(
        String id,
        String type,
        String content,
        String scope,
        int priority,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
}
