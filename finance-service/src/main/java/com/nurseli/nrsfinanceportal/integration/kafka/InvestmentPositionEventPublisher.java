package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.config.FinanceKafkaTopicProperties;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.integration.kafka.event.portfolio.InvestmentPositionClosedEvent;
import com.nurseli.nrsfinanceportal.integration.kafka.event.portfolio.InvestmentPositionCreatedEvent;
import com.nurseli.nrsfinanceportal.integration.kafka.event.portfolio.InvestmentPositionUpdatedEvent;
import com.nurseli.nrsfinanceportal.service.portfolio.ManualPositionTryMetricsCalculator;
import com.nurseli.nrsfinanceportal.service.portfolio.ManualPositionTryMetricsCalculator.PositionMarketMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentPositionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FinanceKafkaTopicProperties topicProperties;
    private final ManualPositionTryMetricsCalculator metricsCalculator;

    @Value("${app.kafka.listeners-enabled:true}")
    private boolean listenersEnabled;

    public void publishCreated(ManualPortfolioPosition p) {
        if (!listenersEnabled) {
            return;
        }
        try {
            PositionMarketMetrics m = metricsCalculator.compute(p);
            var event = new InvestmentPositionCreatedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    p.getUser().getId(),
                    p.getId(),
                    p.getType().name(),
                    p.getSymbol(),
                    p.getQuantity(),
                    p.getBuyDate(),
                    p.getBuyPrice(),
                    "TRY",
                    m.buyResolvedDate(),
                    "USER_MANUAL",
                    m.currentPrice(),
                    m.currentValueTry(),
                    m.investedAmountTry(),
                    m.nominalProfitTry(),
                    m.realProfitTry(),
                    "OPEN"
            );
            send(topicProperties.getInvestmentPositionCreated(), p.getUser().getId().toString(), event);
        } catch (Exception e) {
            log.warn("[KAFKA][INVESTMENT_POSITION] publish created failed positionId={}: {}", p.getId(), e.getMessage());
        }
    }

    public void publishUpdated(ManualPortfolioPosition p) {
        if (!listenersEnabled) {
            return;
        }
        try {
            PositionMarketMetrics m = metricsCalculator.compute(p);
            var event = new InvestmentPositionUpdatedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    p.getUser().getId(),
                    p.getId(),
                    p.getType().name(),
                    p.getSymbol(),
                    p.getQuantity(),
                    p.getBuyDate(),
                    p.getBuyPrice(),
                    "TRY",
                    m.currentPrice(),
                    m.currentValueTry(),
                    m.investedAmountTry(),
                    m.nominalProfitTry(),
                    m.realProfitTry(),
                    "OPEN"
            );
            send(topicProperties.getInvestmentPositionUpdated(), p.getUser().getId().toString(), event);
        } catch (Exception e) {
            log.warn("[KAFKA][INVESTMENT_POSITION] publish updated failed positionId={}: {}", p.getId(), e.getMessage());
        }
    }

    /**
     * Pozisyon silinmeden önce çağrılmalıdır. Kapama, mark-to-market fiyat ile modellenir.
     */
    public void publishClosed(ManualPortfolioPosition p) {
        if (!listenersEnabled) {
            return;
        }
        try {
            PositionMarketMetrics m = metricsCalculator.compute(p);
            BigDecimal sellPrice = m.currentPrice() != null ? m.currentPrice() : BigDecimal.ZERO;
            BigDecimal sellValue = m.currentValueTry() != null
                    ? m.currentValueTry()
                    : sellPrice.multiply(nz(p.getQuantity())).setScale(8, RoundingMode.HALF_UP);
            BigDecimal realized = sellValue.subtract(m.investedAmountTry()).setScale(8, RoundingMode.HALF_UP);
            LocalDate sellDate = LocalDate.now();
            var event = new InvestmentPositionClosedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    p.getUser().getId(),
                    p.getId(),
                    p.getType().name(),
                    p.getSymbol(),
                    p.getQuantity(),
                    sellDate,
                    sellPrice,
                    "TRY",
                    Instant.now(),
                    "MARK_TO_MARKET_ON_DELETE",
                    m.investedAmountTry(),
                    sellValue,
                    realized,
                    m.realProfitTry(),
                    "CLOSED"
            );
            send(topicProperties.getInvestmentPositionClosed(), p.getUser().getId().toString(), event);
        } catch (Exception e) {
            log.warn("[KAFKA][INVESTMENT_POSITION] publish closed failed positionId={}: {}", p.getId(), e.getMessage());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void send(String topic, String key, Object payload) {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        var message = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, key)
                .setHeader(CORRELATION_ID_HEADER, correlationId != null ? correlationId : "")
                .build();
        kafkaTemplate.send(message);
        log.info("[KAFKA][INVESTMENT_POSITION] topic={} key={} correlationId={}", topic, key, correlationId);
    }
}
