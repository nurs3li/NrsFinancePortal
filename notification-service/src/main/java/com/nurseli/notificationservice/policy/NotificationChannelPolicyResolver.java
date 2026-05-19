package com.nurseli.notificationservice.policy;

import com.nurseli.notificationservice.event.NotificationRequestedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationChannelPolicyResolver {

    private final Map<String, DeliveryDecision> typeBasedPolicy = Map.ofEntries(
            // Kullanıcı bildirimleri
            Map.entry("ACCOUNT_FROZEN", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("ACCOUNT_UNFROZEN", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("REVIEW_COMPLETED", DeliveryDecision.IN_APP_ONLY),
            Map.entry("SUSPICIOUS_ACTIVITY", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("USER_LOGIN_SUSPENDED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("USER_LOGIN_UNSUSPENDED", DeliveryDecision.IN_APP_AND_EMAIL),

            // FM görevleri
            Map.entry("REVIEW_TASK_CREATED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("REVIEW_TASK_REMINDER", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("REVIEW_TASK_ASSIGNED", DeliveryDecision.IN_APP_AND_EMAIL),

            // Admin görevleri / escalation
            Map.entry("FREEZE_APPROVAL_CREATED", DeliveryDecision.IN_APP_AND_EMAIL),
            // finance-service ReviewTaskService ile aynı isim olmalı
            Map.entry("REVIEW_TASK_ESCALATED_CRITICAL", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("USER_REGISTERED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("SYSTEM_ERROR", DeliveryDecision.IN_APP_AND_EMAIL),

            // Fund request bildirimleri
            Map.entry("FUND_REQUEST_CREATED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("FUND_REQUEST_APPROVED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("FUND_REQUEST_REJECTED", DeliveryDecision.IN_APP_AND_EMAIL),

            Map.entry("REAL_RETURN_NEGATIVE", DeliveryDecision.IN_APP_ONLY),
            Map.entry("REAL_RETURN_POSITIVE", DeliveryDecision.IN_APP_ONLY),
            Map.entry("PORTFOLIO_CONCENTRATION_RISK", DeliveryDecision.IN_APP_ONLY),
            Map.entry("PORTFOLIO_EVALUATION_REPORT", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("PRICE_ALERT_TRIGGERED", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("PRICE_ALERT_IN_APP", DeliveryDecision.IN_APP_ONLY)
    );

    public DeliveryDecision decide(NotificationRequestedEvent event) {
        if (event == null || event.type() == null) {
            return DeliveryDecision.IN_APP_ONLY;
        }
        return typeBasedPolicy.getOrDefault(event.type(), DeliveryDecision.IN_APP_ONLY);
    }
}