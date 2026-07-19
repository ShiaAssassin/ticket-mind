package com.ticketmind.model.entity;

public record JwtTokenClaims(
        Long userId,
        String username,
        TokenType tokenType
) {
}
