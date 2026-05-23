package com.nurseli.notificationservice.infrastructure.finance;

import com.nurseli.notificationservice.infrastructure.security.S2SAccessTokenService;
import com.nurseli.notificationservice.support.WebClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceUserClientTest {

  @Mock private S2SAccessTokenService s2sAccessTokenService;

  @BeforeEach
  void token() {
    when(s2sAccessTokenService.getAccessToken()).thenReturn("bearer");
  }

  @Test
  void getBySub_notFound_returnsNull() {
    WebClient client = WebClientTestSupport.jsonClient(HttpStatus.NOT_FOUND, "");
    FinanceUserClient financeUserClient = new FinanceUserClient(s2sAccessTokenService, client);
    ReflectionTestUtils.setField(financeUserClient, "financeBaseUrl", "http://finance");

    assertThat(financeUserClient.getBySub("missing-sub")).isNull();
  }

  @Test
  void getBySub_found_returnsUser() {
    WebClient client =
        WebClientTestSupport.jsonClient(
            HttpStatus.OK,
            "{\"sub\":\"sub-1\",\"email\":\"user@test.com\",\"emailVerified\":true}");
    FinanceUserClient financeUserClient = new FinanceUserClient(s2sAccessTokenService, client);
    ReflectionTestUtils.setField(financeUserClient, "financeBaseUrl", "http://finance");

    FinanceUserInfoResponse user = financeUserClient.getBySub("sub-1");

    assertThat(user).isNotNull();
    assertThat(user.email()).isEqualTo("user@test.com");
    assertThat(user.emailVerified()).isTrue();
  }
}
