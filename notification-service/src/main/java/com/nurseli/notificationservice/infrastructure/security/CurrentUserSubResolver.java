package com.nurseli.notificationservice.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Oturum açmış kullanıcının JWT {@code sub} claim değerini çözer;
 * "me" endpoint'leri için kullanılır.
 */
@Component
public class CurrentUserSubResolver {

    /**
     * {@code getRequiredSub} — SecurityContext'teki JWT {@code sub} claim'ini döner;
     * kimlik doğrulama yoksa veya {@code sub} boşsa istisna fırlatır.
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