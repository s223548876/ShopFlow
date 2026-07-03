package com.shopflow.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTest {

    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> configuration.jwtSecretKey("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET must be at least 32 bytes");
    }

    @Test
    void acceptsSecretWithAtLeast32Bytes() {
        assertThat(configuration.jwtSecretKey("12345678901234567890123456789012").getAlgorithm())
                .isEqualTo("HmacSHA256");
    }
}
