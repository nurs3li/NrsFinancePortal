package com.nurseli.nrsfinanceportal.application.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * finance-service kullanıcı senkron servisi — Keycloak JWT kimliğini finance DB users tablosuyla hizalar.
 */
@RequiredArgsConstructor
@Service

public class UserSyncService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * {@code provisionFromAccessToken} — Access token payload'ından kullanıcı bilgilerini çıkarıp DB kaydını oluşturur veya günceller.
     */
    @Transactional
    public User provisionFromAccessToken(String accessToken) {
        JsonNode claims = parseJwtPayload(accessToken);
        String keycloakUserId = text(claims, "sub");
        if (!StringUtils.hasText(keycloakUserId)) {
            throw new IllegalStateException("JWT subject (sub) bulunamadı");
        }
        String email = text(claims, "email");
        String username = text(claims, "preferred_username");
        if (!StringUtils.hasText(username)) {
            username = text(claims, "username");
        }
        Role role = resolveRole(claims);
        Boolean emailVerified = claims.has("email_verified") && !claims.get("email_verified").isNull()
                ? claims.get("email_verified").asBoolean()
                : null;

        String firstName = text(claims, "given_name");
        String lastName = text(claims, "family_name");
        return provision(keycloakUserId, email, username, firstName, lastName, role, emailVerified);
    }

    /**
     * {@code provision} — Keycloak user ID ve profil alanlarıyla User kaydını oluşturur veya günceller (ad-soyad dahil veya kısa overload).
     */
    @Transactional
    public User provision(
            String keycloakUserId,
            String email,
            String username,
            String firstName,
            String lastName,
            Role role,
            Boolean emailVerified) {
        Optional<User> existing = userRepository.findByKeycloakUserId(keycloakUserId);
        User user = existing.orElseGet(() -> User.createFromIdentity(
                keycloakUserId,
                email,
                username,
                role != null ? role : Role.USER
        ));

        if (role != null && user.getRole() != role) {
            user.setRole(role);
        }
        if (StringUtils.hasText(email)) {
            user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(username)) {
            user.setUsername(username.trim());
        }
        if (StringUtils.hasText(firstName)) {
            user.setFirstName(firstName.trim());
        }
        if (StringUtils.hasText(lastName)) {
            user.setLastName(lastName.trim());
        }
        if (emailVerified != null) {
            user.setEmailVerified(emailVerified);
        }

        return userRepository.save(user);
    }

    /**
     * {@code provision} — Keycloak user ID ve profil alanlarıyla User kaydını oluşturur veya günceller (ad-soyad dahil veya kısa overload).
     */
    @Transactional
    public User provision(String keycloakUserId, String email, String username, Role role, Boolean emailVerified) {
        return provision(keycloakUserId, email, username, null, null, role, emailVerified);
    }

    private Role resolveRole(JsonNode claims) {
        List<String> roles = extractRealmRoles(claims);
        if (roles.contains("ADMIN")) {
            return Role.ADMIN;
        }
        return Role.USER;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(JsonNode claims) {
        JsonNode realmAccess = claims.get("realm_access");
        if (realmAccess == null || !realmAccess.isObject()) {
            return List.of();
        }
        JsonNode roles = realmAccess.get("roles");
        if (roles == null || !roles.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(roles.spliterator(), false)
                .map(JsonNode::asText)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * {@code extractSubject} — Access token JWT payload'ından Keycloak user ID (sub) çıkarır.
     */
    public String extractSubject(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        return text(parseJwtPayload(accessToken), "sub");
    }

    private JsonNode parseJwtPayload(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Geçersiz JWT");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("JWT parse edilemedi", e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return StringUtils.hasText(v) ? v : null;
    }
}
