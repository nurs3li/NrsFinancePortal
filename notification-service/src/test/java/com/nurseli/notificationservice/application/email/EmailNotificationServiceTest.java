package com.nurseli.notificationservice.application.email;

import com.nurseli.notificationservice.application.contact.UserEmailResolver;
import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.application.policy.NotificationChannelPolicyResolver;
import com.nurseli.notificationservice.domain.email.EmailDeliveryStatus;
import com.nurseli.notificationservice.infrastructure.gmail.GmailClient;
import com.nurseli.notificationservice.infrastructure.redis.NotificationDedupService;
import com.nurseli.notificationservice.infrastructure.redis.NotificationRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private NotificationChannelPolicyResolver policyResolver;
    @Mock
    private UserEmailResolver userEmailResolver;
    @Mock
    private GmailClient gmailClient;
    @Mock
    private NotificationRateLimitService rateLimitService;
    @Mock
    private NotificationDedupService dedupService;
    @Mock
    private EmailAuditService emailAuditService;

    @InjectMocks
    private EmailNotificationService emailNotificationService;

    private NotificationRequestedEvent emailEvent;

    @BeforeEach
    void setUp() {
        emailEvent =
                new NotificationRequestedEvent(
                        "sub-1",
                        "Custom subject",
                        "Custom body",
                        "USER_REGISTERED",
                        "USER",
                        1L);
    }

    @Test
    void sendIfEligible_nullEvent_noOp() {
        emailNotificationService.sendIfEligible(null);
        verify(emailAuditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendIfEligible_inAppOnly_skipsGmailAndAuditsPolicy() {
        when(policyResolver.decide(emailEvent))
                .thenReturn(com.nurseli.notificationservice.domain.policy.DeliveryDecision.IN_APP_ONLY);

        emailNotificationService.sendIfEligible(emailEvent);

        verify(gmailClient, never()).sendEmail(any(), any(), any());
        verify(emailAuditService)
                .record(
                        eq("sub-1"),
                        isNull(),
                        eq("USER_REGISTERED"),
                        eq(EmailDeliveryStatus.SKIPPED_POLICY),
                        eq("policy=IN_APP_ONLY"),
                        eq("USER"),
                        eq(1L));
    }

    @Test
    void sendIfEligible_rateLimited_skipsSend() {
        when(policyResolver.decide(emailEvent))
                .thenReturn(
                        com.nurseli.notificationservice.domain.policy.DeliveryDecision.IN_APP_AND_EMAIL);
        when(rateLimitService.allow(anyString(), any(Duration.class), anyLong())).thenReturn(false);

        emailNotificationService.sendIfEligible(emailEvent);

        verify(gmailClient, never()).sendEmail(any(), any(), any());
        verify(emailAuditService)
                .record(
                        any(),
                        isNull(),
                        eq("USER_REGISTERED"),
                        eq(EmailDeliveryStatus.SKIPPED_RATE_LIMIT),
                        eq("rate_limit_exceeded"),
                        any(),
                        any());
    }

    @Test
    void sendIfEligible_duplicate_skipsSend() {
        when(policyResolver.decide(emailEvent))
                .thenReturn(
                        com.nurseli.notificationservice.domain.policy.DeliveryDecision.IN_APP_AND_EMAIL);
        when(rateLimitService.allow(anyString(), any(Duration.class), anyLong())).thenReturn(true);
        when(dedupService.firstTime(anyString(), any(Duration.class))).thenReturn(false);

        emailNotificationService.sendIfEligible(emailEvent);

        verify(gmailClient, never()).sendEmail(any(), any(), any());
        verify(emailAuditService)
                .record(
                        any(),
                        isNull(),
                        any(),
                        eq(EmailDeliveryStatus.SKIPPED_DEDUP),
                        eq("duplicate_suppressed"),
                        any(),
                        any());
    }

    @Test
    void sendIfEligible_noEmailResolved_recordsFailure() {
        when(policyResolver.decide(emailEvent))
                .thenReturn(
                        com.nurseli.notificationservice.domain.policy.DeliveryDecision.IN_APP_AND_EMAIL);
        when(rateLimitService.allow(anyString(), any(Duration.class), anyLong())).thenReturn(true);
        when(dedupService.firstTime(anyString(), any(Duration.class))).thenReturn(true);
        when(userEmailResolver.resolveEmail("sub-1")).thenReturn(null);

        emailNotificationService.sendIfEligible(emailEvent);

        verify(gmailClient, never()).sendEmail(any(), any(), any());
        verify(emailAuditService)
                .record(
                        eq("sub-1"),
                        isNull(),
                        eq("USER_REGISTERED"),
                        eq(EmailDeliveryStatus.FAILED_PROVIDER),
                        eq("email_resolution_failed"),
                        any(),
                        any());
    }

    @Test
    void sendIfEligible_happyPath_sendsViaGmail() {
        when(policyResolver.decide(emailEvent))
                .thenReturn(
                        com.nurseli.notificationservice.domain.policy.DeliveryDecision.IN_APP_AND_EMAIL);
        when(rateLimitService.allow(anyString(), any(Duration.class), anyLong())).thenReturn(true);
        when(dedupService.firstTime(anyString(), any(Duration.class))).thenReturn(true);
        when(userEmailResolver.resolveEmail("sub-1")).thenReturn("user@example.com");

        emailNotificationService.sendIfEligible(emailEvent);

        verify(gmailClient).sendEmail("user@example.com", "Custom subject", "Custom body");
        verify(emailAuditService)
                .record(
                        eq("sub-1"),
                        eq("user@example.com"),
                        eq("USER_REGISTERED"),
                        eq(EmailDeliveryStatus.SENT),
                        isNull(),
                        eq("USER"),
                        eq(1L));
    }

    @Test
    void sendIfEligible_gmailFailure_recordsFailureWithoutRethrow() {
        when(policyResolver.decide(emailEvent))
                .thenReturn(
                        com.nurseli.notificationservice.domain.policy.DeliveryDecision.IN_APP_AND_EMAIL);
        when(rateLimitService.allow(anyString(), any(Duration.class), anyLong())).thenReturn(true);
        when(dedupService.firstTime(anyString(), any(Duration.class))).thenReturn(true);
        when(userEmailResolver.resolveEmail("sub-1")).thenReturn("user@example.com");
        org.mockito.Mockito.doThrow(new IllegalStateException("gmail down"))
                .when(gmailClient)
                .sendEmail(any(), any(), any());

        emailNotificationService.sendIfEligible(emailEvent);

        verify(emailAuditService)
                .record(
                        eq("sub-1"),
                        eq("user@example.com"),
                        eq("USER_REGISTERED"),
                        eq(EmailDeliveryStatus.FAILED_PROVIDER),
                        eq("gmail down"),
                        any(),
                        any());
    }
}
