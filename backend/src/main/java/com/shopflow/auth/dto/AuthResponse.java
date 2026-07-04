package com.shopflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"accessToken", "tokenType", "expiresIn"})
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
