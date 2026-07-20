package com.ticketmind.model.dto;

import java.util.List;

public record ChatHistoryResponse(
        String sessionId,
        List<ChatMessageHistoryItem> messages
) {
}
