package com.ticketmind.model.dto;

import java.util.List;

public record ChatHistoryResponse(
        Long sessionId,
        List<ChatMessageHistoryItem> messages
) {
}
