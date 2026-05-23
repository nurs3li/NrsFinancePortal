package com.nurseli.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

  private final CorsConfig config = new CorsConfig();

  @Test
  void corsConfigurationSource_allowsLocalDevOriginsWithCredentials() {
    CorsConfigurationSource source = config.corsConfigurationSource();
    CorsConfiguration cors =
        source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/notifications/me"));

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowCredentials()).isTrue();
    assertThat(cors.getAllowedOrigins())
        .containsExactly("http://localhost:5173", "http://localhost:3000");
    assertThat(cors.getAllowedMethods())
        .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    assertThat(cors.getAllowedHeaders()).containsExactly("*");
  }
}
