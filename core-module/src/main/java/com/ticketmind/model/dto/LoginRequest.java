package com.ticketmind.model.dto;

public record LoginRequest(
        String username,
        String password
) {
}
