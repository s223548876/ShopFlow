package com.shopflow.common.security;

import java.security.Principal;

public record AuthenticatedUser(Long userId, String role) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
