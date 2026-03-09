package com.nurseli.notificationservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserSubResolver {

    /**
     * JWT sub claim (Keycloak user id). "Me" endpoint'leri için kullanılır.
     */
    public String getRequiredSub() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("JWT authentication required");
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("JWT subject (sub) is missing");
        }
        return sub;
    }
}