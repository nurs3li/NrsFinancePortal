package com.nurseli.logconsumer.integration.support;

import java.util.UUID;

public final class LogConsumerIntegrationFixtures {

    public static final String LOG_INDEX_DATE = "2026-05-08";
    public static final String LOG_INDEX_NAME = "application-logs-" + LOG_INDEX_DATE;

    private LogConsumerIntegrationFixtures() {
    }

    public static String uniqueCorrelationId() {
        return "it-corr-" + UUID.randomUUID();
    }

    public static String applicationLogPayload(String correlationId, String message) {
        return """
                {
                  "timestamp":"2026-05-08T12:00:00Z",
                  "level":"INFO",
                  "serviceName":"finance-service",
                  "message":"%s",
                  "correlationId":"%s",
                  "traceId":"trace-it-1",
                  "spanId":"span-it-1",
                  "userId":"user-it-1",
                  "actionType":"LOGIN",
                  "username":"integration_user"
                }
                """.formatted(message, correlationId);
    }
}
