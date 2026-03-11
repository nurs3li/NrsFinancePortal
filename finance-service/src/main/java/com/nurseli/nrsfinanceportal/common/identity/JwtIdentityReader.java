package com.nurseli.nrsfinanceportal.common.identity;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class JwtIdentityReader {

    public String getRequiredSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("JWT authentication not found");
        }

        return jwt.getSubject();
    }

    public String getEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        return jwt.getClaimAsString("email");
    }

    public String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        return jwt.getClaimAsString("preferred_username");
    }
    public Boolean getEmailVerified() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        Boolean v = jwt.getClaim("email_verified");
        return v;
    }
    /**
     * JWT realm_access.roles'dan uygulama rolünü döner.
     * Öncelik: ADMIN > FINANCE_MANAGER > USER (ilk eşleşen kullanılır).
     */
    public Role getRealmRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Role.USER;
        }
        List<String> roles = extractRealmRoles(jwt);
        if (roles.contains("ADMIN")) return Role.ADMIN;
        if (roles.contains("FINANCE_MANAGER")) return Role.FINANCE_MANAGER;
        return Role.USER;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> c)) {
            return List.of();
        }
        return c.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(s -> !s.isBlank())
                .toList();
    }
}