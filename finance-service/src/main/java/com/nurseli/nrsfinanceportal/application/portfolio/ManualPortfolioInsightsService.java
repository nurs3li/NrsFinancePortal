package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.*;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.application.user.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * finance-service manuel portfolio insight servisi — reel getiri, konsantrasyon, sağlık skoru ve bildirim değerlendirmesi üretir.
 */
@Service
public class ManualPortfolioInsightsService {

    private final ManualPortfolioService manualPortfolioService;
    private final CurrentUserResolver currentUserResolver;
    private final MarketDataClient marketDataClient;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;
    private final ManualPortfolioRealReturnCalculator realReturnCalculator;
    private final ManualPortfolioCpiSupport cpiSupport;
    private final PortfolioConcentrationRiskService concentrationRiskService;
    private final PortfolioHealthScoreService healthScoreService;
    private final PortfolioInsightNotificationEvaluator notificationEvaluator;
    private final PortfolioInsightNotificationPublisher notificationPublisher;

    public ManualPortfolioInsightsService(
            @Lazy ManualPortfolioService manualPortfolioService,
            CurrentUserResolver currentUserResolver,
            MarketDataClient marketDataClient,
            ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator,
            ManualPortfolioRealReturnCalculator realReturnCalculator,
            ManualPortfolioCpiSupport cpiSupport,
            PortfolioConcentrationRiskService concentrationRiskService,
            PortfolioHealthScoreService healthScoreService,
            PortfolioInsightNotificationEvaluator notificationEvaluator,
            PortfolioInsightNotificationPublisher notificationPublisher
    ) {
        this.manualPortfolioService = manualPortfolioService;
        this.currentUserResolver = currentUserResolver;
        this.marketDataClient = marketDataClient;
        this.nominalAnalysisCalculator = nominalAnalysisCalculator;
        this.realReturnCalculator = realReturnCalculator;
        this.cpiSupport = cpiSupport;
        this.concentrationRiskService = concentrationRiskService;
        this.healthScoreService = healthScoreService;
        this.notificationEvaluator = notificationEvaluator;
        this.notificationPublisher = notificationPublisher;
    }

    /**
     * {@code buildInsightsForPositions} — Materialized read warm-up için insight hesaplar.
     */
    @Transactional(readOnly = true)
    public ManualPortfolioInsightsResponse buildInsightsForPositions(List<ManualPortfolioPosition> positions) {
        return buildInsights(positions);
    }

    /**
     * {@code insightsForCurrentUser} — Kullanıcının manuel portfolio insight özetini, skorunu ve mesajlarını hesaplar.
     */
    @Transactional(readOnly = true)
    public ManualPortfolioInsightsResponse insightsForCurrentUser() {
        currentUserResolver.getOrCreateCurrentUser();
        List<ManualPortfolioPosition> positions = manualPortfolioService.listMine();
        return buildInsights(positions);
    }

    /**
     * {@code evaluateNotificationsForCurrentUser} — Insight verisine göre portfolio değerlendirme bildirimini oluşturup Kafka'ya yayımlar.
     */
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
        if (positions == null || positions.isEmpty()) {
            return emptyInsights();
        }
        CpiIndexLookup cpiLookup = cpiSupport.loadForPositions(positions);
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

    private static ManualPortfolioInsightsResponse emptyInsights() {
        PortfolioInsightsSummaryDto summary = new PortfolioInsightsSummaryDto(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                false,
                "Manuel pozisyon yok."
        );
        PortfolioHealthScoreDto health = new PortfolioHealthScoreDto(
                0,
                "NEUTRAL",
                "Değerlendirilecek pozisyon yok.",
                List.of()
        );
        PortfolioConcentrationRiskDto concentration = new PortfolioConcentrationRiskDto(
                null,
                null,
                null,
                "LOW",
                "Konsantrasyon riski hesaplanmadı."
        );
        return new ManualPortfolioInsightsResponse(summary, health, concentration, List.of());
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
