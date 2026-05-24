package com.nurseli.logconsumer.integration;

import com.nurseli.logconsumer.integration.support.LogConsumerIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.indices.GetIndexRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLogsIndexIntegrationTest extends LogConsumerIntegrationTestBase {

    @Test
    void startup_createsTodayApplicationLogsIndex() throws Exception {
        String todayIndex = "application-logs-"
                + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        boolean exists = opensearchClient.indices()
                .exists(new GetIndexRequest(todayIndex), RequestOptions.DEFAULT);

        assertThat(exists).isTrue();
    }
}
