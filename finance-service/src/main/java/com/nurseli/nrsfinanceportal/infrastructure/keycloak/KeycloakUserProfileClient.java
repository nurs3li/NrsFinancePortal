package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * Keycloak kullanıcı profil alanları güncelleme.
 */
@Component
@RequiredArgsConstructor
public class KeycloakUserProfileClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Set<String> READ_ONLY_USER_FIELDS = Set.of(
            "access", "self", "userProfileMetadata", "federatedIdentities");

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Client yapılandırması hazır mı döner.
     */
    public boolean isConfigured() {
        return tokenProvider.isConfigured();
    }

    /**
     * Yapılandırma eksikse hata fırlatır.
     */
    public void requireConfigured() {
        tokenProvider.requireConfigured();
    }

    /**
     * Keycloak kullanıcı adını günceller.
     */
    public void updateUsername(String keycloakUserId, String username) {
        updateFields(keycloakUserId, node -> node.put("username", username));
    }

    /**
     * Keycloak e-posta ve emailVerified alanlarını günceller.
     */
    public void updateEmail(String keycloakUserId, String email, boolean emailVerified) {
        updateFields(keycloakUserId, node -> {
            node.put("email", email);
            node.put("emailVerified", emailVerified);
        });
    }

    /**
     * Keycloak ad/soyad alanlarını günceller.
     */
    public void updateFullName(String keycloakUserId, String firstName, String lastName) {
        updateFields(keycloakUserId, node -> {
            if (firstName != null) {
                node.put("firstName", firstName);
            } else {
                node.remove("firstName");
            }
            if (lastName != null) {
                node.put("lastName", lastName);
            } else {
                node.remove("lastName");
            }
        });
    }

    /**
     * Keycloak kullanıcı şifresini resetler.
     */
    public void setPassword(String keycloakUserId, String rawPassword) {
        requireConfigured();
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String realm = properties.getRealm();
        ObjectNode cred = objectMapper.createObjectNode();
        cred.put("type", "password");
        cred.put("temporary", false);
        cred.put("value", rawPassword);

        keycloakAdminWebClient
                .put()
                .uri(base + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/reset-password")
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cred.toString())
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak şifre güncellenemedi " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    private void updateFields(String keycloakUserId, java.util.function.Consumer<ObjectNode> mutator) {
        requireConfigured();
        try {
            JsonNode representation = fetchUserRepresentation(keycloakUserId);
            ObjectNode node = (ObjectNode) objectMapper.readTree(representation.toString());
            mutator.accept(node);
            sanitizeUserForPut(node);
            putUserRepresentation(keycloakUserId, objectMapper.writeValueAsString(node));
        } catch (IllegalStateException e) {
            throw mapProfileUpdateFailure(e);
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak profil güncellenemedi", e);
        }
    }

    private static ObjectNode sanitizeUserForPut(ObjectNode node) {
        READ_ONLY_USER_FIELDS.forEach(node::remove);
        return node;
    }

    private IllegalStateException mapProfileUpdateFailure(IllegalStateException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("400") && msg.toLowerCase().contains("username")) {
            return new IllegalStateException(
                    "Keycloak kullanıcı adı değiştirilemedi. Realm ayarında Edit username açık olmalı (editUsernameAllowed).",
                    e);
        }
        if (msg.contains("409") || msg.toLowerCase().contains("exists")) {
            return new IllegalStateException("Bu kullanıcı adı Keycloak'ta zaten kayıtlı.", e);
        }
        return e;
    }

    private void putUserRepresentation(String keycloakUserId, String body) {
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String uri = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId;

        keycloakAdminWebClient
                .put()
                .uri(base + uri)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak PUT kullanıcı " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    private JsonNode fetchUserRepresentation(String keycloakUserId) {
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String uri = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId;

        String json = keycloakAdminWebClient
                .get()
                .uri(base + uri)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.error(new IllegalStateException("Keycloak'ta kullanıcı bulunamadı"));
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(msg -> Mono.error(new IllegalStateException(
                                    "Keycloak GET kullanıcı " + response.statusCode() + ": " + msg)));
                })
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        try {
            if (!StringUtils.hasText(json)) {
                throw new IllegalStateException("Keycloak kullanıcı bilgisi boş");
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak kullanıcı bilgisi okunamadı", e);
        }
    }
}
