package com.nurseli.logconsumer.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

  private final OpenApiConfig config = new OpenApiConfig();

  @Test
  void logConsumerOpenApi_registersServiceMetadata() {
    OpenAPI openApi = config.logConsumerOpenApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("NRS Log Consumer Service API");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
    assertThat(openApi.getInfo().getDescription()).contains("Log consumer");
  }
}
