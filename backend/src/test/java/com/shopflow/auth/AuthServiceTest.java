package com.shopflow.auth;

import com.shopflow.auth.dto.AuthResponse;
import com.shopflow.auth.dto.LoginRequest;
import com.shopflow.auth.dto.RegisterRequest;
import com.shopflow.auth.dto.RegisterResponse;
import com.shopflow.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                authenticationManager,
                jwtService
        );
    }

    @Test
    void registerAlwaysAssignsCustomerAndHashesPassword() {
        Role customer = new Role(RoleName.CUSTOMER);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(customer));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 101L);
            ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-07-03T00:00:00Z"));
            return user;
        });

        RegisterResponse response = authService.register(new RegisterRequest(
                "Alice@Example.com",
                "correct-horse-42",
                "Alice"
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(response.role()).isEqualTo(RoleName.CUSTOMER.name());
        assertThat(saved.getRole().getName()).isEqualTo(RoleName.CUSTOMER);
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-42");
        assertThat(passwordEncoder.matches("correct-horse-42", saved.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedBeforeSaving() {
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "alice@example.com",
                "correct-horse-42",
                "Alice"
        ))).isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void validCredentialsReturnBearerAccessToken() {
        UserPrincipal principal = new UserPrincipal(101L, "hash", RoleName.CUSTOMER.name());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.issue(101L, RoleName.CUSTOMER.name()))
                .thenReturn(new JwtService.IssuedToken("signed-token", 1800));

        AuthResponse response = authService.login(new LoginRequest(
                "alice@example.com",
                "correct-horse-42"
        ));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
    }

    @Test
    void wrongEmailAndWrongPasswordUseTheSameFailure() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(
                "missing@example.com",
                "any-password"
        ))).isInstanceOf(InvalidCredentialsException.class);

        assertThatThrownBy(() -> authService.login(new LoginRequest(
                "alice@example.com",
                "wrong-password"
        ))).isInstanceOf(InvalidCredentialsException.class);
    }
}
