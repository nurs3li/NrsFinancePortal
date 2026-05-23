package com.nurseli.logconsumer.infrastructure.messaging;

import com.nurseli.logconsumer.application.ApplicationLogIndexerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationLogsConsumerTest {

  @Mock private ApplicationLogIndexerService indexer;

  @InjectMocks private ApplicationLogsConsumer consumer;

  @Test
  void consume_validPayload_delegatesToIndexer() {
    consumer.consume("{\"level\":\"INFO\",\"message\":\"ok\"}");

    verify(indexer).indexLog("{\"level\":\"INFO\",\"message\":\"ok\"}");
  }

  @Test
  void consume_blankPayload_skipsIndexer() {
    consumer.consume("   ");

    verify(indexer, never()).indexLog(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void consume_nullPayload_skipsIndexer() {
    consumer.consume(null);

    verify(indexer, never()).indexLog(org.mockito.ArgumentMatchers.anyString());
  }
}
