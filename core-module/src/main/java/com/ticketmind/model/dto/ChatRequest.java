package com.ticketmind.model.dto;

public record ChatRequest(
        Long sessionId,
        String message
) {
}
