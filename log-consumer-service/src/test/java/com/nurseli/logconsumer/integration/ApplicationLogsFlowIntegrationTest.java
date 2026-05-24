package com.nurseli.logconsumer.integration;

import com.nurseli.logconsumer.integration.support.LogConsumerIntegrationFixtures;
import com.nurseli.logconsumer.integration.support.LogConsumerIntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLogsFlowIntegrationTest extends LogConsumerIntegrationTestBase {

    @Test
    void kafkaApplicationLog_isIndexedInOpenSearch() throws Exception {
        String correlationId = LogConsumerIntegrationFixtures.uniqueCorrelationId();
        String message = "integration test log " + correlationId;
        String payload = LogConsumerIntegrationFixtures.applicationLogPayload(correlationId, message);

        publishApplicationLog(payload);

        Map<String, Object> source = awaitIndexedLog(
                LogConsumerIntegrationFixtures.LOG_INDEX_NAME,
                correlationId,
                Duration.ofSeconds(20));

        assertThat(source.get("level")).isEqualTo("INFO");
        assertThat(source.get("serviceName")).isEqualTo("finance-service");
        assertThat(source.get("message")).isEqualTo(message);
        assertThat(source.get("correlationId")).isEqualTo(correlationId);
        assertThat(source.get("userId")).isEqualTo("user-it-1");
        assertThat(source.get("actionType")).isEqualTo("LOGIN");
        assertThat(source.get("username")).isEqualTo("integration_user");
        assertThat(source.get("traceId")).isEqualTo("trace-it-1");
        assertThat(source.get("timestamp")).isEqualTo("2026-05-08T12:00:00Z");
    }
}
