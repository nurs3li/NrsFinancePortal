package com.nurseli.notificationservice.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailClient {

    private static final String GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1";

    private final GmailTokenService gmailTokenService;
    private final GmailProperties gmailProperties;
    private final MimeMessageBuilder mimeMessageBuilder;

    private final WebClient webClient = WebClient.builder().baseUrl(GMAIL_API_BASE).build();

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

    public record GmailSendRequest(String raw) {}

    public record GmailSendResponse(String id, String threadId) {}
}