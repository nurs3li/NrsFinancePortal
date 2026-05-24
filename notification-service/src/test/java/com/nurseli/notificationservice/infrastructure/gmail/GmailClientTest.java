package com.nurseli.notificationservice.infrastructure.gmail;

import com.nurseli.notificationservice.support.WebClientTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GmailClientTest {

  @Mock private GmailTokenService gmailTokenService;
  @Mock private MimeMessageBuilder mimeMessageBuilder;

  @Test
  void sendEmail_missingFromAddress_throws() {
    GmailProperties props = new GmailProperties();
    GmailClient client = new GmailClient(gmailTokenService, props, mimeMessageBuilder);

    assertThatThrownBy(() -> client.sendEmail("to@test.com", "S", "B"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fromAddress is not configured");
  }

  @Test
  void sendEmail_success_callsGmailApi() {
    GmailProperties props = new GmailProperties();
    props.setFromAddress("from@test.com");
    when(gmailTokenService.getAccessToken()).thenReturn("access");
    when(mimeMessageBuilder.buildRawMessage("from@test.com", "to@test.com", "Sub", "Body"))
        .thenReturn("raw-b64");
    WebClient webClient =
        WebClientTestSupport.jsonClient(HttpStatus.OK, "{\"id\":\"msg-1\",\"threadId\":\"t-1\"}");
    GmailClient client = new GmailClient(gmailTokenService, props, mimeMessageBuilder, webClient);

    client.sendEmail("to@test.com", "Sub", "Body");

    verify(gmailTokenService).getAccessToken();
    verify(mimeMessageBuilder).buildRawMessage("from@test.com", "to@test.com", "Sub", "Body");
  }
}
