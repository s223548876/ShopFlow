package com.shopflow.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShopFlowJwtAuthenticationConverter
        implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        String role = jwt.getClaimAsString("role");
        if (!(userIdClaim instanceof Number userId) || !isSupportedRole(role)) {
            throw new BadJwtException("Required JWT claims are missing or invalid");
        }

        AuthenticatedUser principal = new AuthenticatedUser(userId.longValue(), role);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private boolean isSupportedRole(String role) {
        return "CUSTOMER".equals(role) || "ADMIN".equals(role);
    }
}
