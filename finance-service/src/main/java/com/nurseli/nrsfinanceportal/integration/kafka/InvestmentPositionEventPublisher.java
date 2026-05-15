package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.config.FinanceKafkaTopicProperties;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
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
import java.time.ZoneId;
import java.util.UUID;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentPositionEventPublisher {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

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
                    buyResolvedInstant(p),
                    p.getBuyPriceSource().name(),
                    m.currentPrice(),
                    m.currentValueTry(),
                    m.investedAmountTry(),
                    m.nominalProfitTry(),
                    m.realProfitTry(),
                    p.getStatus().name()
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
                    p.getStatus().name()
            );
            send(topicProperties.getInvestmentPositionUpdated(), p.getUser().getId().toString(), event);
        } catch (Exception e) {
            log.warn("[KAFKA][INVESTMENT_POSITION] publish updated failed positionId={}: {}", p.getId(), e.getMessage());
        }
    }

    /**
     * Kapalı pozisyon: {@link ManualPositionStatus#SOLD} ise gerçek satış alanları; silme öncesi OPEN ise mark-to-market.
     */
    public void publishClosed(ManualPortfolioPosition p) {
        if (!listenersEnabled) {
            return;
        }
        try {
            if (p.getStatus() == ManualPositionStatus.SOLD
                    && p.getSellDate() != null
                    && p.getSellPrice() != null
                    && p.getSellPrice().signum() > 0) {
                PositionMarketMetrics m = metricsCalculator.compute(p);
                BigDecimal qty = nz(p.getQuantity());
                BigDecimal sellFee = p.getSellFee() != null && p.getSellFee().signum() > 0 ? p.getSellFee() : BigDecimal.ZERO;
                BigDecimal sellProceeds = p.getSellPrice().multiply(qty).subtract(sellFee).setScale(8, RoundingMode.HALF_UP);
                BigDecimal realized = sellProceeds.subtract(m.investedAmountTry()).setScale(8, RoundingMode.HALF_UP);
                Instant sellResolved = p.getSellPriceResolvedDate() != null
                        ? p.getSellPriceResolvedDate().atStartOfDay(TZ).toInstant()
                        : p.getSellDate().atStartOfDay(TZ).toInstant();
                String sellSrc = p.getSellPriceSource() != null ? p.getSellPriceSource().name() : "USER_INPUT";
                var event = new InvestmentPositionClosedEvent(
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        p.getUser().getId(),
                        p.getId(),
                        p.getType().name(),
                        p.getSymbol(),
                        p.getQuantity(),
                        p.getSellDate(),
                        p.getSellPrice(),
                        "TRY",
                        sellResolved,
                        sellSrc,
                        m.investedAmountTry(),
                        sellProceeds,
                        realized,
                        m.realProfitTry(),
                        "CLOSED"
                );
                send(topicProperties.getInvestmentPositionClosed(), p.getUser().getId().toString(), event);
                return;
            }

            PositionMarketMetrics m = metricsCalculator.compute(p);
            BigDecimal sellPrice = m.currentPrice() != null ? m.currentPrice() : BigDecimal.ZERO;
            BigDecimal sellValue = m.currentValueTry() != null
                    ? m.currentValueTry()
                    : sellPrice.multiply(nz(p.getQuantity())).setScale(8, RoundingMode.HALF_UP);
            BigDecimal realized = sellValue.subtract(m.investedAmountTry()).setScale(8, RoundingMode.HALF_UP);
            LocalDate sellDate = LocalDate.now(TZ);
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

    private static Instant buyResolvedInstant(ManualPortfolioPosition p) {
        if (p.getBuyPriceResolvedDate() != null) {
            return p.getBuyPriceResolvedDate().atStartOfDay(TZ).toInstant();
        }
        return p.getBuyDate() != null ? p.getBuyDate().atStartOfDay(TZ).toInstant() : Instant.now();
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
