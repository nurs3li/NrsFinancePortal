package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Keycloak OpenID token endpoint client; password ve refresh_token grant.
 */
@Component
@RequiredArgsConstructor
public class KeycloakTokenClient {

    public enum TokenErrorKind {
        INVALID_CREDENTIALS,
        OTP_REQUIRED,
        ACCOUNT_DISABLED,
        OTHER
    }

    public record TokenError(TokenErrorKind kind, String message) {}

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final KeycloakAdminProperties adminProperties;
    private final KeycloakPasswordGrantProperties grantProperties;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Refresh token ile yeni access/refresh token çifti alır.
     */
    public KeycloakTokenResponse refreshTokens(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("Refresh token zorunludur.");
        }
        String base = KeycloakAdminTokenProvider.normalizeBase(adminProperties.getServerUrl());
        String tokenUri = base + "/realms/" + adminProperties.getRealm() + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", grantProperties.getClientId());
        if (StringUtils.hasText(grantProperties.getClientSecret())) {
            form.add("client_secret", grantProperties.getClientSecret());
        }
        form.add("refresh_token", refreshToken.trim());

        String body = keycloakAdminWebClient
                .post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(resp -> resp.bodyToMono(String.class).defaultIfEmpty("").map(b -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        return b;
                    }
                    TokenError err = classifyTokenError(resp.statusCode().value(), b);
                    throw new KeycloakTokenException(err);
                }))
                .timeout(TIMEOUT)
                .block();

        return parseTokenBody(body);
    }

    /**
     * Password grant (+ isteğe bağlı TOTP) ile token çifti alır.
     */
    public KeycloakTokenResponse obtainTokens(String username, String password, String otp, boolean rememberMe) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Kullanıcı adı ve şifre zorunludur.");
        }

        String base = KeycloakAdminTokenProvider.normalizeBase(adminProperties.getServerUrl());
        String tokenUri = base + "/realms/" + adminProperties.getRealm() + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", grantProperties.getClientId());
        if (StringUtils.hasText(grantProperties.getClientSecret())) {
            form.add("client_secret", grantProperties.getClientSecret());
        }
        form.add("username", username.trim());
        form.add("password", password);
        form.add("scope", rememberMe ? "openid profile email offline_access" : "openid profile email");
        if (StringUtils.hasText(otp)) {
            form.add("totp", otp.trim());
        }

        String body = keycloakAdminWebClient
                .post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(resp -> resp.bodyToMono(String.class).defaultIfEmpty("").map(b -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        return b;
                    }
                    TokenError err = classifyTokenError(resp.statusCode().value(), b);
                    throw new KeycloakTokenException(err);
                }))
                .timeout(TIMEOUT)
                .block();

        return parseTokenBody(body);
    }

    private KeycloakTokenResponse parseTokenBody(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String access = text(node, "access_token");
            if (!StringUtils.hasText(access)) {
                throw new IllegalStateException("Keycloak access_token yanıtta yok");
            }
            return new KeycloakTokenResponse(
                    access,
                    text(node, "refresh_token"),
                    intOrNull(node, "expires_in"),
                    intOrNull(node, "refresh_expires_in")
            );
        } catch (KeycloakTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak token yanıtı okunamadı", e);
        }
    }

    private TokenError classifyTokenError(int status, String body) {
        String error = "";
        String description = "";
        try {
            JsonNode node = objectMapper.readTree(body);
            error = text(node, "error");
            description = text(node, "error_description");
        } catch (Exception ignored) {
            description = body != null ? body : "";
        }
        String blob = ((error + " " + description).toLowerCase());

        if (blob.contains("disabled") || blob.contains("account_disabled")) {
            return new TokenError(TokenErrorKind.ACCOUNT_DISABLED,
                    "Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.");
        }
        if (blob.contains("not fully set up")) {
            return new TokenError(TokenErrorKind.INVALID_CREDENTIALS,
                    "Hesap profili eksik. Kayıt sırasında ad ve soyad girilmelidir.");
        }
        if (blob.contains("invalid totp")
                || blob.contains("invalid authenticator")
                || (blob.contains("totp") && blob.contains("invalid"))
                || blob.contains("missing totp")) {
            return new TokenError(TokenErrorKind.OTP_REQUIRED,
                    "İki aşamalı doğrulama kodu gerekli veya hatalı.");
        }
        if (blob.contains("otp required") || blob.contains("totp required")) {
            return new TokenError(TokenErrorKind.OTP_REQUIRED,
                    "İki aşamalı doğrulama kodu gerekli.");
        }
        if (status == 400 || status == 401) {
            return new TokenError(TokenErrorKind.INVALID_CREDENTIALS,
                    "Kullanıcı adı, şifre veya doğrulama kodu hatalı.");
        }
        return new TokenError(TokenErrorKind.OTHER,
                StringUtils.hasText(description) ? description : "Giriş başarısız (HTTP " + status + ")");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    public static class KeycloakTokenException extends RuntimeException {
        private final TokenError error;

        public KeycloakTokenException(TokenError error) {
            super(error.message());
            this.error = error;
        }

        public TokenError error() {
            return error;
        }
    }
}
