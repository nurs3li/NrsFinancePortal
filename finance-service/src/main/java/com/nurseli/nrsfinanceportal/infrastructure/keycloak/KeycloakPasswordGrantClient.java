package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import com.nurseli.nrsfinanceportal.config.KeycloakPasswordGrantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Keycloak password grant ile kullanıcı token alma (legacy/yedek).
 */
@Component
@RequiredArgsConstructor
public class KeycloakPasswordGrantClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final KeycloakAdminProperties adminProperties;
    private final KeycloakPasswordGrantProperties grantProperties;
    private final WebClient keycloakAdminWebClient;

    /**
     * Password grant ile kimlik bilgisi doğrulaması yapar.
     */
    public boolean verifyCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return false;
        }
        String base = KeycloakAdminTokenProvider.normalizeBase(adminProperties.getServerUrl());
        String realm = adminProperties.getRealm();
        String tokenUri = base + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", grantProperties.getClientId());
        if (StringUtils.hasText(grantProperties.getClientSecret())) {
            form.add("client_secret", grantProperties.getClientSecret());
        }
        form.add("username", username.trim());
        form.add("password", password);
        form.add("scope", "openid profile email");

        Integer status = keycloakAdminWebClient
                .post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(resp -> Mono.just(resp.statusCode().value()))
                .timeout(TIMEOUT)
                .block();

        if (status == null) {
            throw new IllegalStateException("Keycloak şifre doğrulaması yanıt vermedi");
        }
        if (status == 200) {
            return true;
        }
        if (status == 400 || status == 401) {
            return false;
        }
        throw new IllegalStateException("Keycloak şifre doğrulaması başarısız: HTTP " + status);
    }
}
