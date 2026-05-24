package com.nurseli.logconsumer.infrastructure.messaging;

import com.nurseli.logconsumer.application.event.TransactionReversedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TransactionReversedConsumerTest {

  private final TransactionReversedConsumer consumer = new TransactionReversedConsumer();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void consume_setsAndClearsCorrelationIdInMdc() {
    TransactionReversedEvent event = sampleEvent();

    consumer.consume("corr-reversed", event);

    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void consume_withoutCorrelationId_doesNotThrow() {
    assertThatCode(() -> consumer.consume("", sampleEvent())).doesNotThrowAnyException();
  }

  private static TransactionReversedEvent sampleEvent() {
    return new TransactionReversedEvent(
        901L,
        801L,
        202L,
        404L,
        new BigDecimal("250.00"),
        new BigDecimal("4750.00"),
        Instant.parse("2026-05-08T11:30:00Z"));
  }
}
