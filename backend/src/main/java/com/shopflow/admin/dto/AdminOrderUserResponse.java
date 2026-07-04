package com.shopflow.admin.dto;

import com.shopflow.auth.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"id", "email", "displayName"})
public record AdminOrderUserResponse(Long id, String email, String displayName) {

    public static AdminOrderUserResponse from(User user) {
        return new AdminOrderUserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
