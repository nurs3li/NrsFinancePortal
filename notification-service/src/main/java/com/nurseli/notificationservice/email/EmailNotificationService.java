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

    // Basit default değerler – istersen config'e taşıyabilirsin
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
            emailAuditService.record(
                    sub,
                    null,
                    type,
                    EmailDeliveryStatus.SKIPPED_POLICY,
                    "policy=IN_APP_ONLY",
                    refType,
                    refId
            );
            return;
        }

        // 2) Rate limit (sub + type)
        String rateKey = "email:rate:" + sub + ":" + type;
        if (!rateLimitService.allow(rateKey, RATE_WINDOW, RATE_MAX_PER_WINDOW)) {
            emailAuditService.record(
                    sub,
                    null,
                    type,
                    EmailDeliveryStatus.SKIPPED_RATE_LIMIT,
                    "rate_limit_exceeded",
                    refType,
                    refId
            );
            return;
        }

        // 3) Dedup (sub + type + reference)
        String dedupKey = "email:dedup:" + type + ":" + sub +
                ":" + (refType != null ? refType : "") +
                ":" + (refId != null ? refId : "");
        if (!dedupService.firstTime(dedupKey, DEDUP_TTL)) {
            emailAuditService.record(
                    sub,
                    null,
                    type,
                    EmailDeliveryStatus.SKIPPED_DEDUP,
                    "duplicate_suppressed",
                    refType,
                    refId
            );
            return;
        }

        // 4) Hedef e-posta çöz
        String to = userEmailResolver.resolveEmail(sub);
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL] No email resolved for sub={}, skipping. type={}", sub, type);
            emailAuditService.record(
                    sub,
                    null,
                    type,
                    EmailDeliveryStatus.FAILED_PROVIDER,
                    "email_resolution_failed",
                    refType,
                    refId
            );
            return;
        }

        String subject = buildSubject(event);
        String body = buildBody(event);

        try {
            log.info("[EMAIL] Sending email via Gmail API to={} type={} subject={}", to, type, subject);
            gmailClient.sendEmail(to, subject, body);
            emailAuditService.record(
                    sub,
                    to,
                    type,
                    EmailDeliveryStatus.SENT,
                    null,
                    refType,
                    refId
            );
        } catch (Exception e) {
            log.error("[EMAIL] Failed to send email to={} type={} subject={}", to, type, subject, e);
            emailAuditService.record(
                    sub,
                    to,
                    type,
                    EmailDeliveryStatus.FAILED_PROVIDER,
                    e.getMessage(),
                    refType,
                    refId
            );
        }
    }

    /**
     * finance-service tam konu ve gövde üretir; burada önce olaydaki title/body kullanılır.
     * Başlık boşsa tip için güvenli varsayılan (eski istemciler / sistem olayları).
     */
    private String buildSubject(NotificationRequestedEvent event) {
        if (event.title() != null && !event.title().isBlank()) {
            return event.title().trim();
        }
        String type = event.type();
        if (type == null) {
            return "Bildirim";
        }
        return switch (type) {
            case "ACCOUNT_FROZEN" -> "Hesabınız güvenlik nedeniyle donduruldu";
            case "ACCOUNT_UNFROZEN" -> "Hesabınız tekrar kullanıma açıldı";
            case "REVIEW_TASK_CREATED" -> "Yeni inceleme görevi (havuz)";
            case "FUND_REQUEST_CREATED" -> "Manuel inceleme bekleyen para talebi";
            case "FUND_REQUEST_APPROVED" -> "Para talebiniz onaylandı";
            case "FUND_REQUEST_REJECTED" -> "Para talebiniz reddedildi";
            case "USER_REGISTERED" -> "Yeni kullanıcı kaydı";
            case "SYSTEM_ERROR" -> "Sistem hatası bildirimi";
            default -> "Bildirim";
        };
    }

    private String buildBody(NotificationRequestedEvent event) {
        if (event.body() != null && !event.body().isBlank()) {
            return event.body();
        }
        return "";
    }
}