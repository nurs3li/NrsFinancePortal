package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

/**
 * Keycloak token endpoint yanıt DTO'su (access/refresh token).
 */
public record KeycloakTokenResponse(
        String accessToken,
        String refreshToken,
        Integer expiresIn,
        Integer refreshExpiresIn
) {}
