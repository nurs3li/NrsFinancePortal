package com.nurseli.notificationservice.application.email;

import com.nurseli.notificationservice.domain.email.EmailDeliveryAudit;
import com.nurseli.notificationservice.domain.email.EmailDeliveryStatus;
import com.nurseli.notificationservice.infrastructure.persistence.EmailDeliveryAuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailAuditServiceTest {

  @Mock private EmailDeliveryAuditRepository repository;

  @InjectMocks private EmailAuditService emailAuditService;

  @Test
  void record_persistsAuditRow() {
    emailAuditService.record(
        "sub-1",
        "u@test.com",
        "USER_REGISTERED",
        EmailDeliveryStatus.SENT,
        null,
        "USER",
        42L);

    ArgumentCaptor<EmailDeliveryAudit> cap = ArgumentCaptor.forClass(EmailDeliveryAudit.class);
    verify(repository).save(cap.capture());
    EmailDeliveryAudit saved = cap.getValue();
    assertThat(ReflectionTestUtils.getField(saved, "targetSub")).isEqualTo("sub-1");
    assertThat(ReflectionTestUtils.getField(saved, "targetEmail")).isEqualTo("u@test.com");
    assertThat(ReflectionTestUtils.getField(saved, "notificationType")).isEqualTo("USER_REGISTERED");
    assertThat(ReflectionTestUtils.getField(saved, "status")).isEqualTo(EmailDeliveryStatus.SENT);
    assertThat(ReflectionTestUtils.getField(saved, "referenceId")).isEqualTo(42L);
  }
}
