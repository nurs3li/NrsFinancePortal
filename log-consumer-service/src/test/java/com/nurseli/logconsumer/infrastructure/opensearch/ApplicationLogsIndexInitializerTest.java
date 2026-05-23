package com.nurseli.logconsumer.infrastructure.opensearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.IndicesClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationLogsIndexInitializerTest {

  @Mock private RestHighLevelClient opensearchClient;
  @Mock private IndicesClient indicesClient;

  @InjectMocks private ApplicationLogsIndexInitializer initializer;

  @Test
  void ensureIndex_createsIndexWhenMissing() throws Exception {
    when(opensearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists(any(GetIndexRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(false);
    when(indicesClient.create(any(CreateIndexRequest.class), eq(RequestOptions.DEFAULT)))
        .thenReturn(org.mockito.Mockito.mock(CreateIndexResponse.class));

    initializer.ensureIndex();

    verify(indicesClient).create(any(CreateIndexRequest.class), eq(RequestOptions.DEFAULT));
  }

  @Test
  void ensureIndex_skipsCreateWhenIndexExists() throws Exception {
    when(opensearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists(any(GetIndexRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(true);

    initializer.ensureIndex();

    verify(indicesClient, never()).create(any(CreateIndexRequest.class), any(RequestOptions.class));
  }

  @Test
  void ensureIndex_swallowsOpenSearchIOException() throws Exception {
    when(opensearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists(any(GetIndexRequest.class), eq(RequestOptions.DEFAULT)))
        .thenThrow(new IOException("cluster down"));

    initializer.ensureIndex();

    verify(indicesClient, never()).create(any(CreateIndexRequest.class), any(RequestOptions.class));
  }

  @Test
  void ensureIndex_usesTodayIndexName() throws Exception {
    when(opensearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists(any(GetIndexRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(false);
    when(indicesClient.create(any(CreateIndexRequest.class), eq(RequestOptions.DEFAULT)))
        .thenReturn(org.mockito.Mockito.mock(CreateIndexResponse.class));

    initializer.ensureIndex();

    String expected =
        "application-logs-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    verify(indicesClient).exists(org.mockito.ArgumentMatchers.argThat((GetIndexRequest req) -> {
      try {
        return req.indices()[0].equals(expected);
      } catch (Exception e) {
        return false;
      }
    }), eq(RequestOptions.DEFAULT));
  }
}
