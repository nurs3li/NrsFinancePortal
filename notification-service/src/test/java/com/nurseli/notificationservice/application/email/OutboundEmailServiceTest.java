package com.nurseli.notificationservice.application.email;

import com.nurseli.notificationservice.domain.email.EmailDeliveryStatus;
import com.nurseli.notificationservice.infrastructure.gmail.GmailClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboundEmailServiceTest {

    @Mock
    private GmailClient gmailClient;

    @Mock
    private EmailAuditService emailAuditService;

    @InjectMocks
    private OutboundEmailService outboundEmailService;

    @Test
    void sendDirect_success_auditsSent() {
        outboundEmailService.sendDirect("a@b.com", "Subj", "Body");

        verify(gmailClient).sendEmail("a@b.com", "Subj", "Body");
        verify(emailAuditService)
                .record(
                        isNull(),
                        eq("a@b.com"),
                        eq(OutboundEmailService.TYPE_INTERNAL_DIRECT),
                        eq(EmailDeliveryStatus.SENT),
                        isNull(),
                        isNull(),
                        isNull());
    }

    @Test
    void sendDirect_failure_auditsAndRethrows() {
        doThrow(new IllegalStateException("smtp"))
                .when(gmailClient)
                .sendEmail("a@b.com", "Subj", "Body");

        assertThatThrownBy(() -> outboundEmailService.sendDirect("a@b.com", "Subj", "Body"))
                .isInstanceOf(IllegalStateException.class);

        verify(emailAuditService)
                .record(
                        isNull(),
                        eq("a@b.com"),
                        eq(OutboundEmailService.TYPE_INTERNAL_DIRECT),
                        eq(EmailDeliveryStatus.FAILED_PROVIDER),
                        eq("smtp"),
                        isNull(),
                        isNull());
    }
}
