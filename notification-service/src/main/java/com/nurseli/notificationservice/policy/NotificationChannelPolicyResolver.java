package com.nurseli.notificationservice.policy;

import com.nurseli.notificationservice.event.NotificationRequestedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationChannelPolicyResolver {

    private final Map<String, DeliveryDecision> typeBasedPolicy = Map.ofEntries(
            // Normal user
            Map.entry("ACCOUNT_FROZEN", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("ACCOUNT_UNFROZEN", DeliveryDecision.IN_APP_AND_EMAIL),
            Map.entry("REVIEW_COMPLETED", DeliveryDecision.IN_APP_ONLY),

            // Finance Manager
            Map.entry("REVIEW_TASK_CREATED", DeliveryDecision.IN_APP_AND_EMAIL),

            // Admin
            Map.entry("WHALE_SPIKE", DeliveryDecision.IN_APP_AND_EMAIL)
    );

    public DeliveryDecision decide(NotificationRequestedEvent event) {
        if (event == null || event.type() == null) {
            return DeliveryDecision.IN_APP_ONLY;
        }
        return typeBasedPolicy.getOrDefault(event.type(), DeliveryDecision.IN_APP_ONLY);
    }
}