package com.ticketmind.model.dto;

public record ChatRequest(
        String sessionId,
        String message
) {
}
