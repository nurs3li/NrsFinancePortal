// notification-service/src/main/java/com/nurseli/notificationservice/email/EmailNotificationService.java
package com.nurseli.notificationservice.email;

import com.nurseli.notificationservice.contact.UserEmailResolver;
import com.nurseli.notificationservice.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.policy.DeliveryDecision;
import com.nurseli.notificationservice.policy.NotificationChannelPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final NotificationChannelPolicyResolver policyResolver;
    private final UserEmailResolver userEmailResolver;
    private final GmailClient gmailClient;
    private final NotificationRateLimitService rateLimitService;
    private final NotificationDedupService dedupService;
    private final EmailAuditService emailAuditService;

    // Basit default değerler – istersen config’e taşıyabilirsin
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final long RATE_MAX_PER_WINDOW = 5;
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    public void sendIfEligible(NotificationRequestedEvent event) {
        if (event == null) {
            return;
        }

        String type = event.type();
        String sub = event.targetKeycloakSub();
        String refType = event.referenceType();
        Long refId = event.referenceId();

        // 1) Policy
        DeliveryDecision decision = policyResolver.decide(event);
        if (decision == DeliveryDecision.IN_APP_ONLY) {
            log.debug("[EMAIL] Skipping email for type={} (IN_APP_ONLY)", type);
            emailAuditService.record(sub, null, type,
                    EmailDeliveryStatus.SKIPPED_POLICY, "policy=IN_APP_ONLY", refType, refId);
            return;
        }

        // 2) Rate limit (sub + type)
        String rateKey = "email:rate:" + sub + ":" + type;
        if (!rateLimitService.allow(rateKey, RATE_WINDOW, RATE_MAX_PER_WINDOW)) {
            emailAuditService.record(sub, null, type,
                    EmailDeliveryStatus.SKIPPED_RATE_LIMIT, "rate_limit_exceeded", refType, refId);
            return;
        }

        // 3) Dedup (sub + type + reference)
        String dedupKey = "email:dedup:" + type + ":" + sub +
                ":" + (refType != null ? refType : "") +
                ":" + (refId != null ? refId : "");
        if (!dedupService.firstTime(dedupKey, DEDUP_TTL)) {
            emailAuditService.record(sub, null, type,
                    EmailDeliveryStatus.SKIPPED_DEDUP, "duplicate_suppressed", refType, refId);
            return;
        }

        // 4) Hedef e‑mail'i çöz
        String to = userEmailResolver.resolveEmail(sub);
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL] No email resolved for sub={}, skipping. type={}", sub, type);
            emailAuditService.record(sub, null, type,
                    EmailDeliveryStatus.FAILED_PROVIDER, "email_resolution_failed", refType, refId);
            return;
        }

        String subject = buildSubject(event);
        String body = buildBody(event);

        try {
            log.info("[EMAIL] Sending email via Gmail API to={} type={} subject={}", to, type, subject);
            gmailClient.sendEmail(to, subject, body);
            emailAuditService.record(sub, to, type,
                    EmailDeliveryStatus.SENT, null, refType, refId);
        } catch (Exception e) {
            log.error("[EMAIL] Failed to send email to={} type={} subject={}", to, type, subject, e);
            emailAuditService.record(sub, to, type,
                    EmailDeliveryStatus.FAILED_PROVIDER, e.getMessage(), refType, refId);
        }
    }

    private String buildSubject(NotificationRequestedEvent event) {
        if ("ACCOUNT_FROZEN".equals(event.type())) {
            return "Hesabınız donduruldu";
        }
        if ("ACCOUNT_UNFROZEN".equals(event.type())) {
            return "Hesabınız yeniden kullanıma açıldı";
        }
        if ("REVIEW_TASK_CREATED".equals(event.type())) {
            return "Yeni inceleme görevi oluşturuldu";
        }
        if ("WHALE_SPIKE".equals(event.type())) {
            return "Whale alert spike tespit edildi";
        }
        return event.title() != null ? event.title() : "Bildirim";
    }

    private String buildBody(NotificationRequestedEvent event) {
        if ("REVIEW_TASK_CREATED".equals(event.type())) {
            StringBuilder sb = new StringBuilder();
            sb.append("Merhaba,\n\n");
            sb.append("Size yeni bir inceleme görevi atandı.\n\n");
            if (event.referenceType() != null && event.referenceId() != null) {
                sb.append("- Görev tipi: ").append(event.referenceType()).append("\n");
                sb.append("- Görev ID: ").append(event.referenceId()).append("\n\n");
            }
            if (event.body() != null && !event.body().isBlank()) {
                sb.append(event.body()).append("\n\n");
            }
            sb.append("NRS Finance Portal üzerinden görev detaylarını görüntüleyebilirsiniz.\n");
            sb.append("İyi çalışmalar.\n");
            return sb.toString();
        }
        return event.body() != null ? event.body() : "";
    }
}