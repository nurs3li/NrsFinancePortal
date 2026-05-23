package com.nurseli.notificationservice.application.email;

import com.nurseli.notificationservice.domain.email.EmailDeliveryStatus;
import com.nurseli.notificationservice.infrastructure.gmail.GmailClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * finance-service internal HTTP e-posta ucu — Kafka policy/dedup dışı doğrudan Gmail API gönderimi + audit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundEmailService {

    public static final String TYPE_INTERNAL_DIRECT = "INTERNAL_DIRECT";

    private final GmailClient gmailClient;
    private final EmailAuditService emailAuditService;

    /**
     * {@code sendDirect} — Policy, dedup ve rate limit olmadan doğrudan e-posta gönderir; başarı/hata audit'e yazılır.
     */
    public void sendDirect(String to, String subject, String body) {
        try {
            gmailClient.sendEmail(to, subject, body);
            emailAuditService.record(
                    null,
                    to,
                    TYPE_INTERNAL_DIRECT,
                    EmailDeliveryStatus.SENT,
                    null,
                    null,
                    null);
            log.info("[OUTBOUND_EMAIL] sent to={} subject={}", to, subject);
        } catch (Exception e) {
            emailAuditService.record(
                    null,
                    to,
                    TYPE_INTERNAL_DIRECT,
                    EmailDeliveryStatus.FAILED_PROVIDER,
                    e.getMessage(),
                    null,
                    null);
            throw e;
        }
    }
}
