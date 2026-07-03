package com.shopflow.auth.dto;

import java.time.Instant;

public record RegisterResponse(
        Long id,
        String email,
        String displayName,
        String role,
        Instant createdAt
) {
}
