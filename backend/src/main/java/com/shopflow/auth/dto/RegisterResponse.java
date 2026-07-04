package com.shopflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(requiredProperties = {"id", "email", "displayName", "role", "createdAt"})
public record RegisterResponse(
        Long id,
        String email,
        String displayName,
        String role,
        Instant createdAt
) {
}
