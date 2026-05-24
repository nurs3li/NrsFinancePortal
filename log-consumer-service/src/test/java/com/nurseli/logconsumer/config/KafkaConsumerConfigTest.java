package com.nurseli.logconsumer.config;

import com.nurseli.logconsumer.application.event.TransactionCreatedEvent;
import com.nurseli.logconsumer.application.event.TransactionReversedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(KafkaConsumerConfig.class);

  @Test
  void registersCreatedReversedAndApplicationLogsFactories() {
    contextRunner
        .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092")
        .run(
            ctx -> {
              assertThat(ctx).hasBean("createdConsumerFactory");
              assertThat(ctx).hasBean("reversedConsumerFactory");
              assertThat(ctx).hasBean("applicationLogsConsumerFactory");
              assertThat(ctx).hasBean("createdKafkaListenerContainerFactory");
              assertThat(ctx).hasBean("reversedKafkaListenerContainerFactory");
              assertThat(ctx).hasBean("applicationLogsKafkaListenerContainerFactory");

              ConsumerFactory<String, TransactionCreatedEvent> created =
                  ctx.getBean("createdConsumerFactory", ConsumerFactory.class);
              ConsumerFactory<String, TransactionReversedEvent> reversed =
                  ctx.getBean("reversedConsumerFactory", ConsumerFactory.class);
              ConsumerFactory<String, String> logs =
                  ctx.getBean("applicationLogsConsumerFactory", ConsumerFactory.class);

              assertThat(created.getConfigurationProperties())
                  .containsEntry("group.id", "log-consumer-created");
              assertThat(reversed.getConfigurationProperties())
                  .containsEntry("group.id", "log-consumer-reversed");
              assertThat(logs.getConfigurationProperties())
                  .containsEntry("group.id", "log-consumer-application-logs");

              ConcurrentKafkaListenerContainerFactory<String, String> logsFactory =
                  ctx.getBean(
                      "applicationLogsKafkaListenerContainerFactory",
                      ConcurrentKafkaListenerContainerFactory.class);
              assertThat(logsFactory.getConsumerFactory()).isNotNull();
            });
  }
}
