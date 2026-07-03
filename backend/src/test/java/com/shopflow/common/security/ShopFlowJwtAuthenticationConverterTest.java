package com.shopflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShopFlowJwtAuthenticationConverterTest {

    @Test
    void principalProvidesUserIdAndRoleWithoutKeepingTheToken() {
        Jwt jwt = Jwt.withTokenValue("sensitive-token")
                .header("alg", "HS256")
                .subject("101")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .claim("userId", 101L)
                .claim("role", "CUSTOMER")
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new ShopFlowJwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedUser(101L, "CUSTOMER"));
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");
    }
}
