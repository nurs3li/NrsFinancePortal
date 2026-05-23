package com.nurseli.notificationservice.application.policy;

import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.domain.policy.DeliveryDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationChannelPolicyResolverTest {

    private NotificationChannelPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NotificationChannelPolicyResolver();
    }

    @Test
    void nullEvent_defaultsToInAppOnly() {
        assertThat(resolver.decide(null)).isEqualTo(DeliveryDecision.IN_APP_ONLY);
    }

    @Test
    void nullType_defaultsToInAppOnly() {
        var event = new NotificationRequestedEvent("sub-1", "t", "b", null, null, null);
        assertThat(resolver.decide(event)).isEqualTo(DeliveryDecision.IN_APP_ONLY);
    }

    @ParameterizedTest
    @CsvSource({
            "USER_LOGIN_SUSPENDED, IN_APP_AND_EMAIL",
            "USER_REGISTERED, IN_APP_AND_EMAIL",
            "REAL_RETURN_NEGATIVE, IN_APP_ONLY",
            "PORTFOLIO_EVALUATION_REPORT, IN_APP_AND_EMAIL",
            "PRICE_ALERT_TRIGGERED, IN_APP_AND_EMAIL",
            "PRICE_ALERT_IN_APP, IN_APP_ONLY"
    })
    void knownTypes_matchPolicy(String type, DeliveryDecision expected) {
        var event = event(type);
        assertThat(resolver.decide(event)).isEqualTo(expected);
    }

    @Test
    void unknownType_defaultsToInAppOnly() {
        assertThat(resolver.decide(event("CUSTOM_UNKNOWN"))).isEqualTo(DeliveryDecision.IN_APP_ONLY);
    }

    private static NotificationRequestedEvent event(String type) {
        return new NotificationRequestedEvent("sub-abc", "Title", "Body", type, "REF", 42L);
    }
}
