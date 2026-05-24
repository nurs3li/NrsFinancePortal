package com.nurseli.notificationservice.infrastructure.gmail;

import com.nurseli.notificationservice.support.WebClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailTokenServiceTest {

  @Test
  void getAccessToken_missingConfig_throws() {
    GmailProperties props = new GmailProperties();
    GmailTokenService service = new GmailTokenService(props);

    assertThatThrownBy(service::getAccessToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("configuration missing");
  }

  @Test
  void getAccessToken_success_returnsToken() {
    GmailProperties props = new GmailProperties();
    props.setClientId("id");
    props.setClientSecret("secret");
    props.setRefreshToken("refresh");
    var webClient =
        WebClientTestSupport.jsonClient(
            HttpStatus.OK, "{\"access_token\":\"tok-123\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
    GmailTokenService service = new GmailTokenService(props, webClient);

    assertThat(service.getAccessToken()).isEqualTo("tok-123");
  }
}
