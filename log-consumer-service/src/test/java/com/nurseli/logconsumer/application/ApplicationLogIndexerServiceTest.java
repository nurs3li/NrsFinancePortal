package com.nurseli.logconsumer.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationLogIndexerServiceTest {

  @Mock private RestHighLevelClient opensearchClient;

  @InjectMocks private ApplicationLogIndexerService service;

  @Test
  void indexLog_indexesParsedFieldsWithDailyIndexName() throws Exception {
    when(opensearchClient.index(any(IndexRequest.class), eq(RequestOptions.DEFAULT)))
        .thenReturn(org.mockito.Mockito.mock(IndexResponse.class));

    String payload =
        """
        {
          "timestamp":"2026-05-08T12:00:00Z",
          "level":"INFO",
          "serviceName":"finance-service",
          "message":"user logged in",
          "correlationId":"corr-1",
          "traceId":"trace-1",
          "spanId":"span-1",
          "userId":"user-9"
        }
        """;

    service.indexLog(payload);

    ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
    verify(opensearchClient).index(captor.capture(), eq(RequestOptions.DEFAULT));

    IndexRequest request = captor.getValue();
    assertThat(request.index()).isEqualTo("application-logs-2026-05-08");
    Map<String, Object> source = request.sourceAsMap();
    assertThat(source.get("level")).isEqualTo("INFO");
    assertThat(source.get("serviceName")).isEqualTo("finance-service");
    assertThat(source.get("message")).isEqualTo("user logged in");
    assertThat(source.get("correlationId")).isEqualTo("corr-1");
    assertThat(source.get("userId")).isEqualTo("user-9");
    assertThat(request.id()).isNotBlank();
  }

  @Test
  void indexLog_normalizesMutableInstantTimestamp() throws Exception {
    when(opensearchClient.index(any(IndexRequest.class), eq(RequestOptions.DEFAULT)))
        .thenReturn(org.mockito.Mockito.mock(IndexResponse.class));

  String payload =
        """
        {
          "timestamp":"MutableInstant[epochSecond=1716400000, nano=0]",
          "level":"WARN",
          "message":"retry"
        }
        """;

    service.indexLog(payload);

    ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
    verify(opensearchClient).index(captor.capture(), eq(RequestOptions.DEFAULT));
    assertThat(captor.getValue().sourceAsMap().get("timestamp"))
        .isEqualTo(java.time.Instant.ofEpochSecond(1716400000L).toString());
  }

  @Test
  void indexLog_usesTodayWhenTimestampMissing() throws Exception {
    when(opensearchClient.index(any(IndexRequest.class), eq(RequestOptions.DEFAULT)))
        .thenReturn(org.mockito.Mockito.mock(IndexResponse.class));

    service.indexLog("{\"level\":\"ERROR\",\"message\":\"boom\"}");

    ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
    verify(opensearchClient).index(captor.capture(), eq(RequestOptions.DEFAULT));
    String expected =
        ApplicationLogIndexerService.INDEX_PREFIX
            + "-"
            + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    assertThat(captor.getValue().index()).isEqualTo(expected);
  }

  @Test
  void indexLog_invalidJson_doesNotCallOpenSearch() throws Exception {
    service.indexLog("not-json");

    verify(opensearchClient, never()).index(any(IndexRequest.class), any(RequestOptions.class));
  }
}
