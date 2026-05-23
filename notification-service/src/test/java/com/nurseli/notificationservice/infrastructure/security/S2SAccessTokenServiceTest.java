package com.nurseli.notificationservice.infrastructure.security;

import com.nurseli.notificationservice.support.WebClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S2SAccessTokenServiceTest {

  private S2SAccessTokenService service;

  @BeforeEach
  void setUp() {
    service =
        new S2SAccessTokenService(
            WebClientTestSupport.jsonClient(
                HttpStatus.OK,
                "{\"access_token\":\"s2s-token\",\"token_type\":\"Bearer\",\"expires_in\":300,\"scope\":\"\"}"));
    ReflectionTestUtils.setField(service, "authServerUrl", "http://keycloak");
    ReflectionTestUtils.setField(service, "realm", "nrs-finance");
    ReflectionTestUtils.setField(service, "clientId", "backend");
    ReflectionTestUtils.setField(service, "clientSecret", "secret");
  }

  @Test
  void getAccessToken_success() {
    assertThat(service.getAccessToken()).isEqualTo("s2s-token");
  }

  @Test
  void getAccessToken_emptyBody_throws() {
    S2SAccessTokenService empty =
        new S2SAccessTokenService(WebClientTestSupport.jsonClient(HttpStatus.OK, "{}"));
    ReflectionTestUtils.setField(empty, "authServerUrl", "http://keycloak");
    ReflectionTestUtils.setField(empty, "realm", "nrs-finance");
    ReflectionTestUtils.setField(empty, "clientId", "backend");
    ReflectionTestUtils.setField(empty, "clientSecret", "secret");

    assertThatThrownBy(empty::getAccessToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to obtain S2S access token");
  }
}
