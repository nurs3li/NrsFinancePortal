package com.nurseli.notificationservice.application;

import com.nurseli.notificationservice.application.email.EmailNotificationService;
import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.domain.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private NotificationOrchestrator orchestrator;

    @Test
    void handle_persistsInAppThenEvaluatesEmail() {
        var event =
                new NotificationRequestedEvent(
                        "sub-1", "Hello", "Body", "USER_REGISTERED", null, null);
        Notification saved =
                Notification.builder().id(10L).userSub("sub-1").title("Hello").type("USER_REGISTERED").build();

        when(notificationService.create(
                        eq("sub-1"), eq("Hello"), eq("Body"), eq("USER_REGISTERED"), eq(null), eq(null)))
                .thenReturn(saved);

        Notification result = orchestrator.handle(event);

        assertThat(result.getId()).isEqualTo(10L);
        verify(emailNotificationService).sendIfEligible(event);
    }

    @Test
    void handle_nullTitleAndType_usesSafeDefaults() {
        var event = new NotificationRequestedEvent("sub-2", null, "b", null, "R", 1L);
        Notification saved = Notification.builder().id(2L).userSub("sub-2").build();
        when(notificationService.create(eq("sub-2"), eq(""), eq("b"), eq("NOTIFICATION"), eq("R"), eq(1L)))
                .thenReturn(saved);

        orchestrator.handle(event);

        verify(notificationService).create("sub-2", "", "b", "NOTIFICATION", "R", 1L);
    }
}
