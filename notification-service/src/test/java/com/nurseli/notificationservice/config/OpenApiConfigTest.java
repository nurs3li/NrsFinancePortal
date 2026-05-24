package com.nurseli.notificationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

  private final OpenApiConfig config = new OpenApiConfig();

  @Test
  void notificationServiceOpenApi_registersBearerJwtScheme() {
    OpenAPI openApi = config.notificationServiceOpenApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("NRS Notification Service API");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
    assertThat(openApi.getSecurity()).hasSize(1);
    assertThat(openApi.getSecurity().getFirst()).containsKey("bearerAuth");

    SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
    assertThat(scheme.getScheme()).isEqualTo("bearer");
    assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
  }
}
