package com.nurseli.notificationservice.infrastructure.messaging;

import com.nurseli.notificationservice.application.NotificationOrchestrator;
import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.domain.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationOrchestrator notificationOrchestrator;

    @InjectMocks
    private NotificationEventConsumer consumer;

    @Test
    void consume_blankSub_skipsOrchestrator() {
        consumer.consume(new NotificationRequestedEvent("  ", "t", "b", "X", null, null));
        verify(notificationOrchestrator, never()).handle(any());
    }

    @Test
    void consume_validEvent_delegatesToOrchestrator() {
        var event = new NotificationRequestedEvent("sub-1", "t", "b", "USER_REGISTERED", null, null);
        when(notificationOrchestrator.handle(event))
                .thenReturn(Notification.builder().id(1L).userSub("sub-1").build());

        consumer.consume(event);

        verify(notificationOrchestrator).handle(event);
    }

    @Test
    void consume_orchestratorThrows_doesNotPropagate() {
        var event = new NotificationRequestedEvent("sub-1", "t", "b", "X", null, null);
        when(notificationOrchestrator.handle(event)).thenThrow(new RuntimeException("db"));

        consumer.consume(event);

        verify(notificationOrchestrator).handle(event);
    }
}
