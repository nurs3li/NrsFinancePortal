package com.nurseli.notificationservice.email;

import com.nurseli.notificationservice.contact.UserEmailResolver;
import com.nurseli.notificationservice.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.policy.DeliveryDecision;
import com.nurseli.notificationservice.policy.NotificationChannelPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final NotificationChannelPolicyResolver policyResolver;
    private final UserEmailResolver userEmailResolver;
    private final GmailClient gmailClient;

    public void sendIfEligible(NotificationRequestedEvent event) {
        if (event == null) {
            return;
        }

        DeliveryDecision decision = policyResolver.decide(event);
        if (decision == DeliveryDecision.IN_APP_ONLY) {
            log.debug("[EMAIL] Skipping email for type={} (IN_APP_ONLY)", event.type());
            return;
        }

        String to = userEmailResolver.resolveEmail(event.targetKeycloakSub());
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL] No email resolved for sub={}, skipping. type={}",
                    event.targetKeycloakSub(), event.type());
            return;
        }

        String subject = buildSubject(event);
        String body = buildBody(event);

        log.info("[EMAIL] Sending email via Gmail API to={} type={} subject={}",
                to, event.type(), subject);

        gmailClient.sendEmail(to, subject, body);
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
        return event.body() != null ? event.body() : "";
    }
}