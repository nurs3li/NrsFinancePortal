package com.nurseli.notificationservice.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserSubResolverTest {

  private final CurrentUserSubResolver resolver = new CurrentUserSubResolver();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getRequiredSub_returnsJwtSubject() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "keycloak-sub-99")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(resolver.getRequiredSub()).isEqualTo("keycloak-sub-99");
  }

  @Test
  void getRequiredSub_noAuthentication_throws() {
    SecurityContextHolder.clearContext();

    assertThatThrownBy(resolver::getRequiredSub)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT authentication required");
  }

  @Test
  void getRequiredSub_blankSub_throws() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claims(c -> c.putAll(Map.of("sub", "   ")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThatThrownBy(resolver::getRequiredSub)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT subject (sub)");
  }
}
