package com.nurseli.logconsumer.consumer;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PortfolioInvestorDomainLogConsumer {

    @KafkaListener(
            topics = "${app.kafka.topics.investment-position-created:finance.investment.position.created}",
            groupId = "log-consumer-investment-created",
            containerFactory = "domainEventsStringKafkaListenerContainerFactory"
    )
    public void onCreated(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            String payload
    ) {
        logDomain("INVESTMENT_POSITION_CREATED", correlationId, payload);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.investment-position-updated:finance.investment.position.updated}",
            groupId = "log-consumer-investment-updated",
            containerFactory = "domainEventsStringKafkaListenerContainerFactory"
    )
    public void onUpdated(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            String payload
    ) {
        logDomain("INVESTMENT_POSITION_UPDATED", correlationId, payload);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.investment-position-closed:finance.investment.position.closed}",
            groupId = "log-consumer-investment-closed",
            containerFactory = "domainEventsStringKafkaListenerContainerFactory"
    )
    public void onClosed(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            String payload
    ) {
        logDomain("INVESTMENT_POSITION_CLOSED", correlationId, payload);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.investor-behavior-updated:investor.behavior.updated}",
            groupId = "log-consumer-investor-behavior",
            containerFactory = "domainEventsStringKafkaListenerContainerFactory"
    )
    public void onInvestorBehavior(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            String payload
    ) {
        logDomain("INVESTOR_BEHAVIOR_UPDATED", correlationId, payload);
    }

    private static void logDomain(String type, String correlationId, String payload) {
        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put("correlationId", correlationId);
            }
            log.info("[KAFKA][DOMAIN] type={} correlationId={} payload={}", type, correlationId, payload);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
