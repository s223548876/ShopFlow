package com.shopflow.admin.dto;

import com.shopflow.auth.User;

public record AdminOrderUserResponse(Long id, String email, String displayName) {

    public static AdminOrderUserResponse from(User user) {
        return new AdminOrderUserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
