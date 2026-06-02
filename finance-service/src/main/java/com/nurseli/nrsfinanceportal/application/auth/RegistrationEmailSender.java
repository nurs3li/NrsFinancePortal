package com.nurseli.nrsfinanceportal.application.auth;

import com.nurseli.nrsfinanceportal.config.NotificationClientProperties;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakAdminTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * finance-service kayıt e-posta gönderici — notification servisi üzerinden doğrulama mailleri yollar.
 */
@Slf4j
@RequiredArgsConstructor
@Service

public class RegistrationEmailSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final NotificationClientProperties notificationClientProperties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;

    /**
     * {@code sendVerificationCode} — Kayıt doğrulama kodunu hedef e-postaya notification API ile gönderir.
     */
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

        postEmail(baseUrl, new EmailSendPayload(targetEmail, subject, body));
        log.info("[REGISTER_CODE] verification mail sent to={}", targetEmail);
    }

    /**
     * {@code sendEmailChangeVerificationCode} — Profil e-posta değişikliği doğrulama kodunu gönderir.
     */
    public void sendEmailChangeVerificationCode(String targetEmail, String code) {
        if (!tokenProvider.isConfigured()) {
            throw new IllegalStateException("E-posta değişikliği için Keycloak admin konfigürasyonu eksik");
        }
        String baseUrl = notificationClientProperties.getBaseUrl();
        String subject = "NRS Finance E-posta Değişikliği Doğrulama Kodu";
        String body = """
                Merhaba,

                E-posta değişikliği doğrulama kodunuz: %s

                Kod 60 saniye içinde girilmelidir.
                Bu işlemi siz yapmadıysanız bu e-postayı yok sayabilirsiniz.
                """.formatted(code);

        postEmail(baseUrl, new EmailSendPayload(targetEmail, subject, body));
        log.info("[PROFILE_EMAIL_CODE] verification mail sent to={}", targetEmail);
    }

    /**
     * {@code sendPasswordResetVerificationCode} — Şifre sıfırlama doğrulama kodunu gönderir.
     */
    public void sendPasswordResetVerificationCode(String targetEmail, String code) {
        if (!tokenProvider.isConfigured()) {
            throw new IllegalStateException("Şifre sıfırlama maili için Keycloak admin konfigürasyonu eksik");
        }
        String baseUrl = notificationClientProperties.getBaseUrl();
        String subject = "NRS Finance Şifre Sıfırlama Kodu";
        String body = """
                Merhaba,

                Şifre sıfırlama doğrulama kodunuz: %s

                Kod 10 dakika içinde geçerliliğini kaybeder.
                Bu işlemi siz yapmadıysanız bu e-postayı yok sayabilirsiniz.
                """.formatted(code);

        postEmail(baseUrl, new EmailSendPayload(targetEmail, subject, body));
        log.info("[PASSWORD_RESET_CODE] verification mail sent to={}", targetEmail);
    }

    private void postEmail(String baseUrl, EmailSendPayload payload) {
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
                                        friendlyEmailFailure(response.statusCode().value(), msg)))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    private static String friendlyEmailFailure(int status, String raw) {
        if (raw != null) {
            if (raw.contains("Google'a bağlanamıyor") || raw.contains("Gmail OAuth")) {
                return "E-posta servisi şu an Google'a bağlanamıyor. Lütfen kısa süre sonra tekrar deneyin.";
            }
            int errIdx = raw.indexOf("\"error\"");
            if (errIdx >= 0) {
                int start = raw.indexOf(':', errIdx);
                int q1 = raw.indexOf('"', start + 1);
                int q2 = raw.indexOf('"', q1 + 1);
                if (q1 >= 0 && q2 > q1) {
                    return raw.substring(q1 + 1, q2);
                }
            }
        }
        return "Doğrulama maili gönderilemedi (HTTP " + status + ").";
    }

    /**
     * EmailSendPayload — Notification servisine gönderilecek e-posta yükü (alıcı, konu, gövde).
     */
    public record EmailSendPayload(String to, String subject, String body) {}
}
