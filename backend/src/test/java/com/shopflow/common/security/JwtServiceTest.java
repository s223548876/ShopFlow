package com.shopflow.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void tokenContainsUserIdentityRoleAndThirtyMinuteExpiry() {
        SecretKey key = new SecretKeySpec(
                "test-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtService jwtService = new JwtService(encoder);

        JwtService.IssuedToken issued = jwtService.issue(101L, "CUSTOMER");
        Jwt decoded = decoder.decode(issued.accessToken());

        assertThat(((Number) decoded.getClaim("userId")).longValue()).isEqualTo(101L);
        assertThat(decoded.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(decoded.getSubject()).isEqualTo("101");
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).toSeconds())
                .isEqualTo(1800);
        assertThat(issued.expiresIn()).isEqualTo(1800);
    }
}
