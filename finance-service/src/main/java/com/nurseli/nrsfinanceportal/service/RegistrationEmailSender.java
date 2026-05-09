package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.config.NotificationClientProperties;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakAdminTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationEmailSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final NotificationClientProperties notificationClientProperties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;

    public void sendVerificationCode(String targetEmail, String code) {
        if (!tokenProvider.isConfigured()) {
            throw new IllegalStateException("Kayıt maili için Keycloak admin konfigürasyonu eksik");
        }
        String baseUrl = notificationClientProperties.getBaseUrl();
        String subject = "NRS Finance Kayıt Doğrulama Kodu";
        String body = """
                Merhaba,
                                
                Kayıt doğrulama kodunuz: %s
                                
                Kod 10 dakika içinde geçerliliğini kaybeder.
                Bu işlemi siz yapmadıysanız bu e-postayı yok sayabilirsiniz.
                """.formatted(code);

        String payload = """
                {"to":"%s","subject":"%s","body":"%s"}
                """.formatted(escape(targetEmail), escape(subject), escape(body));

        keycloakAdminWebClient
                .post()
                .uri(baseUrl + "/api/notifications/internal/email/send")
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Doğrulama maili gönderilemedi " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
        log.info("[REGISTER_CODE] verification mail sent to={}", targetEmail);
    }

    private static String escape(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
