package com.nurseli.notificationservice.infrastructure.gmail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Gmail REST API üzerinden e-posta gönderimini yönetir;
 * OAuth access token ve Base64 URL-encoded MIME mesajı kullanır.
 */
@Slf4j
@Component
public class GmailClient {

    private static final String GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1";

    private final GmailTokenService gmailTokenService;
    private final GmailProperties gmailProperties;
    private final MimeMessageBuilder mimeMessageBuilder;
    private final WebClient webClient;

    @Autowired
    public GmailClient(
            GmailTokenService gmailTokenService,
            GmailProperties gmailProperties,
            MimeMessageBuilder mimeMessageBuilder) {
        this(gmailTokenService, gmailProperties, mimeMessageBuilder,
                WebClient.builder().baseUrl(GMAIL_API_BASE).build());
    }

    /**
     * {@code GmailClient} — Test ve özel {@link WebClient} enjeksiyonu için paket görünümlü kurucu.
     */
    GmailClient(
            GmailTokenService gmailTokenService,
            GmailProperties gmailProperties,
            MimeMessageBuilder mimeMessageBuilder,
            WebClient webClient) {
        this.gmailTokenService = gmailTokenService;
        this.gmailProperties = gmailProperties;
        this.mimeMessageBuilder = mimeMessageBuilder;
        this.webClient = webClient;
    }

    /**
     * {@code sendEmail} — Belirtilen alıcıya düz metin e-posta gönderir;
     * {@code fromAddress} yapılandırılmamışsa veya Gmail API hata dönerse istisna fırlatır.
     */
    public void sendEmail(String to, String subject, String bodyText) {
        String from = gmailProperties.getFromAddress();
        if (from == null || from.isBlank()) {
            log.error("[GMAIL] fromAddress is not configured");
            throw new IllegalStateException("Gmail fromAddress is not configured");
        }

        String accessToken = gmailTokenService.getAccessToken();
        String raw = mimeMessageBuilder.buildRawMessage(from, to, subject, bodyText);

        GmailSendRequest request = new GmailSendRequest(raw);

        try {
            GmailSendResponse response = webClient.post()
                    .uri("/users/me/messages/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Mono.just(request), GmailSendRequest.class)
                    .retrieve()
                    .bodyToMono(GmailSendResponse.class)
                    .block();

            if (response != null && response.id != null) {
                log.info("[GMAIL] Email sent successfully to={} subject={} messageId={}",
                        to, subject, response.id);
            } else {
                log.warn("[GMAIL] Email sent but response is null or missing id. to={} subject={}",
                        to, subject);
            }
        } catch (Exception e) {
            log.error("[GMAIL] Failed to send email to={} subject={}", to, subject, e);
            throw new IllegalStateException("Failed to send email via Gmail API", e);
        }
    }

    /** Gmail {@code messages.send} isteği; {@code raw} alanı Base64 URL-encoded MIME içeriğidir. */
    public record GmailSendRequest(String raw) {}

    /** Gmail {@code messages.send} yanıtı. */
    public record GmailSendResponse(String id, String threadId) {}
}