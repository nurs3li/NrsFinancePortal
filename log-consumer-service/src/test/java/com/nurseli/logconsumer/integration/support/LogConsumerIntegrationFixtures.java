package com.nurseli.logconsumer.integration.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class LogConsumerIntegrationFixtures {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private LogConsumerIntegrationFixtures() {
    }

    public static String todayLogIndexName() {
        return "application-logs-" + LocalDate.now().format(ISO_DATE);
    }

    public static String todayLogTimestamp() {
        return LocalDate.now().format(ISO_DATE) + "T12:00:00Z";
    }

    public static String uniqueCorrelationId() {
        return "it-corr-" + UUID.randomUUID();
    }

    public static String applicationLogPayload(String correlationId, String message) {
        String timestamp = todayLogTimestamp();
        return """
                {
                  "timestamp":"%s",
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
                """.formatted(timestamp, message, correlationId);
    }
}
