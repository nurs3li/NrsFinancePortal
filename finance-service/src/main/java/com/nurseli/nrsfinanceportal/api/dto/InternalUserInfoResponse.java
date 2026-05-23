package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Dahili kullanıcı bilgisi response'u; Keycloak sub, email ve doğrulama durumunu taşır.
 */
public record InternalUserInfoResponse(
        String sub,
        String email,
        boolean emailVerified
) {}