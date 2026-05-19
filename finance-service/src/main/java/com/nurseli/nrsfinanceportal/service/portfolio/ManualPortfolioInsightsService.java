package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.*;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.ManualPortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ManualPortfolioInsightsService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final ManualPortfolioService manualPortfolioService;
    private final CurrentUserResolver currentUserResolver;
    private final MarketDataClient marketDataClient;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;
    private final ManualPortfolioRealReturnCalculator realReturnCalculator;
    private final PortfolioConcentrationRiskService concentrationRiskService;
    private final PortfolioHealthScoreService healthScoreService;
    private final PortfolioInsightNotificationEvaluator notificationEvaluator;
    private final PortfolioInsightNotificationPublisher notificationPublisher;

    @Transactional(readOnly = true)
    public ManualPortfolioInsightsResponse insightsForCurrentUser() {
        currentUserResolver.getOrCreateCurrentUser();
        List<ManualPortfolioPosition> positions = manualPortfolioService.listMine();
        return buildInsights(positions);
    }

    @Transactional(readOnly = true)
    public PortfolioInsightNotificationEvaluateResponse evaluateNotificationsForCurrentUser() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioInsightsResponse insights = insightsForCurrentUser();
        Long refId = user.getId();
        List<NotificationRequestedEvent> events = new ArrayList<>();
        NotificationRequestedEvent report = notificationEvaluator.buildEvaluationReport(
                user.getKeycloakUserId(),
                refId,
                insights
        );
        if (report != null) {
            events.add(report);
        }
        List<String> types = notificationPublisher.publishAll(events);
        return new PortfolioInsightNotificationEvaluateResponse(types.size(), types);
    }

    private ManualPortfolioInsightsResponse buildInsights(List<ManualPortfolioPosition> positions) {
        LocalDate today = LocalDate.now(TZ);
        CpiIndexLookup cpiLookup = loadCpiForPositions(positions, today);
        LatestPricingSnapshot pricing = marketDataClient.loadLatestPricing();

        ManualPortfolioRealReturnCalculator.PortfolioRealReturnResult real =
                realReturnCalculator.compute(positions, cpiLookup, pricing);

        PortfolioConcentrationRiskDto concentration =
                concentrationRiskService.evaluate(positions, pricing);

        Map<AssetType, BigDecimal> openValueByType = openValueByAssetType(positions, pricing);
        PortfolioHealthScoreDto health = healthScoreService.evaluate(real, concentration, positions, openValueByType);

        PortfolioInsightsSummaryDto summary = new PortfolioInsightsSummaryDto(
                real.openCurrentValue(),
                real.closedRealizedValue(),
                real.totalEvaluatedValue(),
                real.totalInvestedAmount(),
                real.nominalReturn(),
                real.nominalReturnPct(),
                real.inflationAdjustedCost(),
                real.realReturn(),
                real.realReturnPct(),
                real.realReturnAvailable(),
                real.realReturnUnavailableReason()
        );

        List<PortfolioInsightItemDto> insightItems = buildInsightItems(summary, concentration, health);

        return new ManualPortfolioInsightsResponse(summary, health, concentration, insightItems);
    }

    private CpiIndexLookup loadCpiForPositions(List<ManualPortfolioPosition> positions, LocalDate today) {
        if (positions == null || positions.isEmpty()) {
            return CpiIndexLookup.empty();
        }
        LocalDate min = null;
        LocalDate max = today;
        for (ManualPortfolioPosition p : positions) {
            if (p.getBuyDate() != null) {
                min = min == null || p.getBuyDate().isBefore(min) ? p.getBuyDate() : min;
            }
            if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null) {
                max = p.getSellDate().isAfter(max) ? p.getSellDate() : max;
            }
        }
        if (min == null) {
            return CpiIndexLookup.empty();
        }
        return marketDataClient.loadCpiIndexLookup(min, max);
    }

    private Map<AssetType, BigDecimal> openValueByAssetType(
            List<ManualPortfolioPosition> positions,
            LatestPricingSnapshot pricing) {
        Map<AssetType, BigDecimal> map = new HashMap<>();
        for (ManualPortfolioPosition p : positions) {
            if (p.getStatus() != ManualPositionStatus.OPEN) {
                continue;
            }
            var analysis = nominalAnalysisCalculator.computeWithCurrentPrice(
                    p,
                    marketDataClient.getPriceTry(p.getType(), p.getSymbol(), pricing)
            );
            BigDecimal val = analysis.currentValue();
            if (val != null && val.signum() > 0) {
                map.merge(p.getType(), val, BigDecimal::add);
            }
        }
        return map;
    }

    private List<PortfolioInsightItemDto> buildInsightItems(
            PortfolioInsightsSummaryDto summary,
            PortfolioConcentrationRiskDto concentration,
            PortfolioHealthScoreDto health) {

        List<PortfolioInsightItemDto> items = new ArrayList<>();

        if (summary.nominalReturn() != null) {
            String sev = summary.nominalReturn().compareTo(BigDecimal.ZERO) >= 0 ? "INFO" : "WARNING";
            items.add(new PortfolioInsightItemDto(
                    "NOMINAL_RETURN",
                    sev,
                    String.format("Nominal getiri: %s TRY", summary.nominalReturn().toPlainString())));
        }

        if (summary.realReturnAvailable() && summary.realReturn() != null) {
            String sev = summary.realReturn().compareTo(BigDecimal.ZERO) >= 0 ? "INFO" : "WARNING";
            items.add(new PortfolioInsightItemDto(
                    "REAL_RETURN",
                    sev,
                    String.format("Reel getiri: %s TRY", summary.realReturn().toPlainString())));
        } else if (!summary.realReturnAvailable()) {
            items.add(new PortfolioInsightItemDto(
                    "REAL_RETURN",
                    "INFO",
                    summary.realReturnUnavailableReason() != null
                            ? summary.realReturnUnavailableReason()
                            : "Reel getiri hesaplanamadı."));
        }

        if (concentration.riskLevel() != null) {
            String sev = switch (concentration.riskLevel()) {
                case "HIGH" -> "CRITICAL";
                case "MEDIUM" -> "WARNING";
                default -> "INFO";
            };
            items.add(new PortfolioInsightItemDto("CONCENTRATION", sev, concentration.message()));
        }

        items.add(new PortfolioInsightItemDto(
                "HEALTH_SCORE",
                health.level().equals("WEAK") ? "WARNING" : "INFO",
                String.format("Sağlık skoru: %d (%s) — %s", health.score(), health.level(), health.summary())));

        return items;
    }
}
