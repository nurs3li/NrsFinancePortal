package com.nurseli.notificationservice.application.policy;

import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.domain.policy.DeliveryDecision;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Bildirim {@code type} değerine göre kanal policy'sini ({@link DeliveryDecision}) çözer.
 */
@Component
public class NotificationChannelPolicyResolver {

    private final Map<String, DeliveryDecision> typeBasedPolicy = Map.ofEntries(
            Map.entry("USER_LOGIN_SUSPENDED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("USER_LOGIN_UNSUSPENDED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("USER_REGISTERED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("SYSTEM_ERROR", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("REAL_RETURN_NEGATIVE", DeliveryDecision.IN_APP_ONLY),
            Map.entry("REAL_RETURN_POSITIVE", DeliveryDecision.IN_APP_ONLY),
            Map.entry("PORTFOLIO_CONCENTRATION_RISK", DeliveryDecision.IN_APP_ONLY),
            Map.entry("PORTFOLIO_EVALUATION_REPORT", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("PRICE_ALERT_TRIGGERED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("PRICE_ALERT_IN_APP", DeliveryDecision.IN_APP_ONLY)
    );

    /**
     * {@code decide} — Olay tipine göre e-posta kanalının açık olup olmadığını belirler; bilinmeyen tipler {@code IN_APP_ONLY}.
     */
    public DeliveryDecision decide(NotificationRequestedEvent event) {
        if (event == null || event.type() == null) {
            return DeliveryDecision.IN_APP_ONLY;
        }
        return typeBasedPolicy.getOrDefault(event.type(), DeliveryDecision.IN_APP_ONLY);
    }
}
