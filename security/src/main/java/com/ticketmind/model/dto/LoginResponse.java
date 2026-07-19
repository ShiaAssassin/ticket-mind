package com.ticketmind.model.dto;

public record LoginResponse(
        Long userId,
        String username,
        String displayName,
        String accessToken,
        String refreshToken
) {
}
