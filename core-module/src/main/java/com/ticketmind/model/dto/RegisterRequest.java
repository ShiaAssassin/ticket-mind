package com.ticketmind.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(max = 80)
        String username,

        @NotBlank
        @Size(max = 255)
        String password,

        @Size(max = 120)
        String displayName
) {
}
