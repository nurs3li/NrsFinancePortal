package com.nurseli.logconsumer.config;

import org.junit.jupiter.api.Test;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(OpenSearchConfig.class);

  @Test
  void opensearchClientBean_isCreatedWithConfiguredHost() {
    contextRunner
        .withPropertyValues("opensearch.host=opensearch-test", "opensearch.port=9201")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(RestHighLevelClient.class);
              RestHighLevelClient client = ctx.getBean(RestHighLevelClient.class);
              assertThat(client).isNotNull();
            });
  }
}
