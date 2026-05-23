package com.nurseli.logconsumer.infrastructure.messaging;

import com.nurseli.logconsumer.application.event.TransactionCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionCreatedConsumerTest {

  private final TransactionCreatedConsumer consumer = new TransactionCreatedConsumer();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void consume_setsAndClearsCorrelationIdInMdc() {
    TransactionCreatedEvent event = sampleEvent();

    consumer.consume("corr-created", event);

    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void consume_withoutCorrelationId_doesNotThrow() {
    assertThatCode(() -> consumer.consume(null, sampleEvent())).doesNotThrowAnyException();
  }

  private static TransactionCreatedEvent sampleEvent() {
    return new TransactionCreatedEvent(
        101L,
        202L,
        303L,
        "DEPOSIT",
        new BigDecimal("1500.00"),
        new BigDecimal("5000.00"),
        Instant.parse("2026-05-08T10:00:00Z"));
  }
}
