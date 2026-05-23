package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Keycloak TOTP credential kurulum ve doğrulama.
 */
@Component
@RequiredArgsConstructor
public class KeycloakTotpCredentialClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Kullanıcıya TOTP secret credential kaydeder.
     */
    public void registerTotpSecret(String keycloakUserId, String base32Secret) {
        tokenProvider.requireConfigured();
        ObjectNode cred = objectMapper.createObjectNode();
        cred.put("type", "otp");
        cred.put("temporary", false);
        cred.put("value", base32Secret);

        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String path = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId + "/reset-password";

        keycloakAdminWebClient
                .put()
                .uri(base + path)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cred.toString())
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak TOTP kaydı başarısız " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    /**
     * Kullanıcının OTP credential'larını siler.
     */
    public void deleteOtpCredentials(String keycloakUserId) {
        tokenProvider.requireConfigured();
        for (String credentialId : listOtpCredentialIds(keycloakUserId)) {
            deleteCredential(keycloakUserId, credentialId);
        }
    }

    private List<String> listOtpCredentialIds(String keycloakUserId) {
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String path = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId + "/credentials";

        String json = keycloakAdminWebClient
                .get()
                .uri(base + path)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak credential listesi " + response.statusCode() + ": " + msg))))
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        List<String> ids = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return ids;
            }
            for (JsonNode cred : root) {
                if ("otp".equalsIgnoreCase(cred.path("type").asText(""))) {
                    String id = cred.path("id").asText(null);
                    if (id != null && !id.isBlank()) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak credential listesi okunamadı", e);
        }
        return ids;
    }

    private void deleteCredential(String keycloakUserId, String credentialId) {
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String path = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId
                + "/credentials/" + credentialId;

        keycloakAdminWebClient
                .delete()
                .uri(base + path)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak TOTP silme " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }
}
