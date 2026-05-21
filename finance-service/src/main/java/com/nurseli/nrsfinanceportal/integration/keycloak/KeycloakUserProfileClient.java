package com.nurseli.nrsfinanceportal.integration.keycloak;

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

@Component
@RequiredArgsConstructor
public class KeycloakUserProfileClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return tokenProvider.isConfigured();
    }

    public void requireConfigured() {
        tokenProvider.requireConfigured();
    }

    public void updateUsername(String keycloakUserId, String username) {
        updateFields(keycloakUserId, node -> node.put("username", username));
    }

    public void updateEmail(String keycloakUserId, String email, boolean emailVerified) {
        updateFields(keycloakUserId, node -> {
            node.put("email", email);
            node.put("emailVerified", emailVerified);
        });
    }

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
        JsonNode representation = fetchUserRepresentation(keycloakUserId);
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(representation.toString());
            mutator.accept(node);
            putUserRepresentation(keycloakUserId, objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak profil güncellenemedi", e);
        }
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
