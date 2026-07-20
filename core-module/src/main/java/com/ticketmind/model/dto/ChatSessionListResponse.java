package com.ticketmind.model.dto;

import java.util.List;

public record ChatSessionListResponse(
        List<ChatSessionListItem> sessions
) {
}
