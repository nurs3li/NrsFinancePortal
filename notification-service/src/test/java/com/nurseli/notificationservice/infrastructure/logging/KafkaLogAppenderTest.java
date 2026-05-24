package com.nurseli.notificationservice.infrastructure.logging;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaLogAppenderTest {

  @Test
  void createAppender_usesDefaultsWhenAttributesBlank() {
    KafkaLogAppender appender = KafkaLogAppender.createAppender(null, null, "  ", " ", " ");

    assertThat(appender.getName()).isEqualTo("KafkaLog");
  }

  @Test
  void append_withoutStartedProducer_isNoOp() {
    KafkaLogAppender appender =
        KafkaLogAppender.createAppender("test", null, "application-logs", "localhost:9092", "notification");

    appender.append(sampleEvent("hello"));
  }

  @Test
  void append_sendsJsonWithCorrelationAndServiceName() throws Exception {
    KafkaLogAppender appender =
        KafkaLogAppender.createAppender("test", null, "logs-topic", "localhost:9092", "notification-service");
    @SuppressWarnings("unchecked")
    KafkaProducer<String, String> producer = mock(KafkaProducer.class);
    injectProducer(appender, producer);

    appender.append(sampleEvent("mail sent"));

    ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
    org.mockito.Mockito.verify(producer).send(captor.capture());
    String json = captor.getValue().value();
    assertThat(captor.getValue().topic()).isEqualTo("logs-topic");
    assertThat(json).contains("\"serviceName\":\"notification-service\"");
    assertThat(json).contains("\"message\":\"mail sent\"");
    assertThat(json).contains("\"correlationId\":\"corr-42\"");
    assertThat(json).contains("\"traceId\":\"trace-1\"");
    assertThat(json).contains("\"userId\":\"user-9\"");
  }

  @Test
  void append_swallowsProducerFailures() throws Exception {
    KafkaLogAppender appender =
        KafkaLogAppender.createAppender("test", null, "logs-topic", "localhost:9092", "notification-service");
    @SuppressWarnings("unchecked")
    KafkaProducer<String, String> producer = mock(KafkaProducer.class);
    org.mockito.Mockito.doThrow(new RuntimeException("kafka down")).when(producer).send(any());
    injectProducer(appender, producer);

    appender.append(sampleEvent("fail quietly"));
  }

  private static LogEvent sampleEvent(String message) {
    LogEvent event = mock(LogEvent.class);
    ReadOnlyStringMap ctx = mock(ReadOnlyStringMap.class);
    Message msg = mock(Message.class);

    when(event.getLevel()).thenReturn(Level.INFO);
    when(event.getLoggerName()).thenReturn("com.nurseli.test");
    when(event.getMessage()).thenReturn(msg);
    when(msg.getFormattedMessage()).thenReturn(message);
    when(event.getContextData()).thenReturn(ctx);
    when(ctx.containsKey("correlationId")).thenReturn(true);
    when(ctx.getValue("correlationId")).thenReturn("corr-42");
    when(ctx.containsKey("trace_id")).thenReturn(true);
    when(ctx.getValue("trace_id")).thenReturn("trace-1");
    when(ctx.containsKey("userId")).thenReturn(true);
    when(ctx.getValue("userId")).thenReturn("user-9");
  when(ctx.containsKey(eq("traceId"))).thenReturn(false);
    when(ctx.containsKey(eq("span_id"))).thenReturn(false);
    when(ctx.containsKey(eq("spanId"))).thenReturn(false);
    when(ctx.containsKey(eq("actionType"))).thenReturn(false);
    when(ctx.containsKey(eq("username"))).thenReturn(false);
    return event;
  }

  private static void injectProducer(KafkaLogAppender appender, KafkaProducer<String, String> producer)
      throws Exception {
    Field field = KafkaLogAppender.class.getDeclaredField("producer");
    field.setAccessible(true);
    field.set(appender, producer);
  }
}
