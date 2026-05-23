package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.*;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.event.NotificationRequestedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioInsightNotificationEvaluatorTest {

    private final PortfolioInsightNotificationEvaluator evaluator = new PortfolioInsightNotificationEvaluator();

    @Test
    void publishesNegativePositiveAndHighConcentrationOnly() {
        var summary = new PortfolioInsightsSummaryDto(
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("9"),
                new BigDecimal("-1"),
                new BigDecimal("-10"),
                true,
                null
        );
        var concentration = new PortfolioConcentrationRiskDto(
                "BTC", new BigDecimal("70"), new BigDecimal("90"), "HIGH", "high");
        var health = new PortfolioHealthScoreDto(50, "WEAK", "weak", List.of());
        var insights = new ManualPortfolioInsightsResponse(
                summary, health, concentration, List.of());

        List<NotificationRequestedEvent> events = evaluator.evaluate("sub-1", 42L, insights);

        assertThat(events).hasSize(2);
        assertThat(events.stream().map(NotificationRequestedEvent::type).toList())
                .containsExactlyInAnyOrder(
                        PortfolioInsightNotificationEvaluator.TYPE_REAL_RETURN_NEGATIVE,
                        PortfolioInsightNotificationEvaluator.TYPE_CONCENTRATION_RISK);
    }

    @Test
    void publishesPositiveWhenRealReturnPositive() {
        var summary = new PortfolioInsightsSummaryDto(
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN,
                new BigDecimal("9"), new BigDecimal("2"), new BigDecimal("20"),
                true, null);
        var concentration = new PortfolioConcentrationRiskDto(
                "A", new BigDecimal("30"), new BigDecimal("50"), "LOW", "ok");
        var health = new PortfolioHealthScoreDto(90, "GOOD", "good", List.of());
        var insights = new ManualPortfolioInsightsResponse(summary, health, concentration, List.of());

        List<NotificationRequestedEvent> events = evaluator.evaluate("sub-1", 1L, insights);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(PortfolioInsightNotificationEvaluator.TYPE_REAL_RETURN_POSITIVE);
    }

    @Test
    void skipsWhenRealReturnUnavailable() {
        var summary = new PortfolioInsightsSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null,
                false, "no cpi");
        var concentration = new PortfolioConcentrationRiskDto(
                null, null, null, "LOW", "ok");
        var health = new PortfolioHealthScoreDto(100, "GOOD", "good", List.of());
        var insights = new ManualPortfolioInsightsResponse(summary, health, concentration, List.of());

        assertThat(evaluator.evaluate("sub", 1L, insights)).isEmpty();
    }

    @Test
    void buildEvaluationReportIncludesInsightMessages() {
        var items = List.of(
                new PortfolioInsightItemDto("NOMINAL_RETURN", "INFO", "Nominal getiri: 100 TRY"),
                new PortfolioInsightItemDto("CONCENTRATION", "CRITICAL", "Yoğunlaşma riski yüksek"));
        var insights = new ManualPortfolioInsightsResponse(
                new PortfolioInsightsSummaryDto(
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.ONE, BigDecimal.TEN, null, null, null, false, null),
                new PortfolioHealthScoreDto(75, "MEDIUM", "orta", List.of()),
                new PortfolioConcentrationRiskDto("VOO", new BigDecimal("64.4"), null, "HIGH", "msg"),
                items);

        NotificationRequestedEvent event = evaluator.buildEvaluationReport("sub-1", 99L, insights);

        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo(PortfolioInsightNotificationEvaluator.TYPE_PORTFOLIO_EVALUATION_REPORT);
        assertThat(event.title()).isEqualTo("Portföy değerlendirmeniz");
        assertThat(event.body()).contains("Nominal getiri: 100 TRY");
        assertThat(event.body()).contains("[Risk] Yoğunlaşma riski yüksek");
    }
}
