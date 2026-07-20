package com.ticketmind.model.dto;

public record ChatResponse(
        Long sessionId,
        String answer
) {
}
