package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioConcentrationRiskDto;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiAnalysisRequest;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.application.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioInsightsService;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioNominalAnalysisCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * finance-service portfolio AI context oluşturucu — analiz isteğinden OpenAI prompt'una gidecek portfolio snapshot'ını üretir.
 */
@Component

public class PortfolioAiContextBuilder {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final String PLACEHOLDER_NEWS =
            "Haber özeti bu sürümde sembol bazlı özetlenmedi.";

    private final ManualPortfolioService manualPortfolioService;
    private final ManualPortfolioInsightsService insightsService;
    private final ManualPortfolioNominalAnalysisCalculator nominalCalculator;
    private final MarketDataClient marketDataClient;
    private final ObjectMapper objectMapper;

    /**
     * {@code PortfolioAiContextBuilder} — Bağımlılıkları enjekte eden public constructor.
     */
    public PortfolioAiContextBuilder(
            ManualPortfolioService manualPortfolioService,
            ManualPortfolioInsightsService insightsService,
    ManualPortfolioNominalAnalysisCalculator nominalCalculator,
            MarketDataClient marketDataClient,
            ObjectMapper objectMapper
    ) {
        this.manualPortfolioService = manualPortfolioService;
        this.insightsService = insightsService;
        this.nominalCalculator = nominalCalculator;
        this.marketDataClient = marketDataClient;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code buildSnapshot} — Açık pozisyonlar, makro panel, haberler ve metriklerden PortfolioAiContextSnapshot oluşturur.
     */
    public PortfolioAiContextSnapshot buildSnapshot(PortfolioAiAnalysisRequest request) {
        List<ManualPortfolioPosition> positions = manualPortfolioService.listMine();
        ManualPortfolioSummaryView summary = manualPortfolioService.summaryMine();
    ManualPortfolioInsightsResponse insights = insightsService.insightsForCurrentUser();

        List<ManualPortfolioPosition> open = positions.stream()
                .filter(p -> p.getStatus() == ManualPositionStatus.OPEN)
                .toList();
        List<ManualPortfolioPosition> sold = positions.stream()
                .filter(p -> p.getStatus() == ManualPositionStatus.SOLD)
                .toList();

        BigDecimal openTotal = open.stream()
                .map(p -> nominalCalculator.compute(p).currentValue())
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PortfolioAiContextSnapshot.PositionLine> openLines = new ArrayList<>();
        LocalDate today = LocalDate.now(TZ);
        for (ManualPortfolioPosition p : open) {
            ManualPortfolioNominalAnalysis n = nominalCalculator.compute(p);
            double value = n.currentValue() != null ? n.currentValue().doubleValue() : 0;
            double weight = openTotal.signum() > 0 && value > 0
                    ? n.currentValue().multiply(BigDecimal.valueOf(100))
                    .divide(openTotal, 4, RoundingMode.HALF_UP).doubleValue()
                    : 0;
            Integer holdingDays = p.getBuyDate() != null
                    ? (int) ChronoUnit.DAYS.between(p.getBuyDate(), today)
                    : null;
            openLines.add(new PortfolioAiContextSnapshot.PositionLine(
                    p.getSymbol(),
                    p.getSymbol(),
                    p.getType().name(),
                    "OPEN",
                    p.getQuantity().doubleValue(),
                    n.currentPrice() != null ? n.currentPrice().doubleValue() : null,
                    value,
                    p.getBuyPrice() != null ? p.getBuyPrice().doubleValue() : null,
                    n.buyCost() != null ? n.buyCost().doubleValue() : null,
                    n.unrealizedProfit() != null ? n.unrealizedProfit().doubleValue() : null,
                    n.unrealizedReturnPct() != null ? n.unrealizedReturnPct().doubleValue() : null,
                    weight,
                    holdingDays
            ));
        }

        openLines.sort(Comparator.comparingDouble(PortfolioAiContextSnapshot.PositionLine::weightPct).reversed());
        List<PortfolioAiContextSnapshot.PositionLine> topWeight = openLines.stream().limit(8).toList();

        Comparator<PortfolioAiContextSnapshot.PositionLine> byReturn =
                Comparator.comparing(p -> p.returnPct() != null ? p.returnPct() : 0.0);
        List<PortfolioAiContextSnapshot.PositionLine> topGainers = openLines.stream()
                .filter(p -> p.returnPct() != null)
                .sorted(byReturn.reversed())
                .limit(3)
                .toList();
        List<PortfolioAiContextSnapshot.PositionLine> topLosers = openLines.stream()
                .filter(p -> p.returnPct() != null)
                .sorted(byReturn)
                .limit(3)
                .toList();

        Map<String, Double> allocation = new LinkedHashMap<>();
        for (PortfolioAiContextSnapshot.PositionLine line : openLines) {
            allocation.merge(line.assetClass(), line.weightPct(), Double::sum);
        }

        var ins = insights.summary();
        var conc = insights.concentrationRisk();
        PortfolioAiConcentrationLevel concentrationLevel = mapConcentration(conc);
        String largestSym = conc != null && conc.topAssetSymbol() != null
                ? conc.topAssetSymbol()
                : topWeight.isEmpty() ? null : topWeight.get(0).symbol();
        double largestW = conc != null && conc.topAssetWeightPct() != null
                ? conc.topAssetWeightPct().doubleValue()
                : (topWeight.isEmpty() ? 0 : topWeight.getFirst().weightPct());

        int health = insights.healthScore() != null ? insights.healthScore().score() : 62;

        List<PortfolioAiContextSnapshot.PositionLine> assetTargets = PortfolioAiPositionSelector.mergeAssetCommentTargets(
                openLines,
                topGainers,
                topLosers,
                largestSym,
                concentrationLevel
        );

        Map<String, Object> macro = request.includeMacro() ? loadMacroSummary() : Map.of();
        List<Map<String, Object>> news = List.of();
        boolean newsAvailable = false;

        return new PortfolioAiContextSnapshot(
                request.analysisType(),
                request.riskProfile(),
                request.detailLevel(),
                request.includeNews(),
                request.includeMacro(),
                request.includeRealReturn(),
                summary.getCurrentOpenValue() != null ? summary.getCurrentOpenValue() : openTotal,
                ins != null ? ins.totalInvestedAmount() : null,
                ins != null ? ins.nominalReturn() : null,
                ins != null && ins.nominalReturnPct() != null ? ins.nominalReturnPct().doubleValue() : null,
                ins != null ? ins.realReturn() : null,
                ins != null && ins.realReturnPct() != null ? ins.realReturnPct().doubleValue() : null,
                ins != null && ins.realReturnAvailable(),
                health,
                largestSym,
                largestW,
                concentrationLevel,
                allocation,
                topWeight,
                topGainers,
                topLosers,
                assetTargets,
                open.size(),
                sold.size(),
                macro,
                news,
                newsAvailable
        );
    }

    /**
     * {@code toCompactContextJson} — Snapshot'ı token tasarruflu kompakt JSON context'e dönüştürür.
     */
    public String toCompactContextJson(PortfolioAiContextSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("analysisType", snapshot.analysisType().name());
        root.put("riskProfile", snapshot.riskProfile().name());
    root.put("detailLevel", snapshot.detailLevel().name());
        root.put("totalValueTry", snapshot.totalValueTry());
        root.put("totalCostTry", snapshot.totalCostTry());
        root.put("nominalPnlTry", snapshot.nominalPnlTry());
        root.put("nominalReturnPct", snapshot.nominalReturnPct());
        if (snapshot.includeRealReturn()) {
            root.put("realPnlTry", snapshot.realPnlTry());
            root.put("realReturnPct", snapshot.realReturnPct());
            root.put("realReturnAvailable", snapshot.realReturnAvailable());
        }
        root.put("healthScore", snapshot.healthScore());
        root.put("largestPosition", Map.of(
                "symbol", snapshot.largestPositionSymbol() != null ? snapshot.largestPositionSymbol() : "",
                "weightPct", snapshot.largestPositionWeightPct()
        ));
        root.put("concentrationRisk", snapshot.concentrationLevel().name());
        root.put("allocationByAssetClassPct", snapshot.allocationByAssetClassPct());
        root.put("openPositionCount", snapshot.openCount());
        root.put("soldPositionCount", snapshot.soldCount());
        root.put("assetCommentTargets", snapshot.assetCommentTargets().stream()
                .map(this::compactAssetLine)
                .toList());
        if (snapshot.includeMacro()) {
            root.put("macro", PortfolioAiMacroCompact.compact(snapshot.macroSummary()));
        }
        if (snapshot.includeNews()) {
            if (snapshot.newsAvailable() && !snapshot.newsSummaries().isEmpty()) {
                root.put("newsAvailability", "AVAILABLE");
                root.put("newsSummaries", snapshot.newsSummaries().stream().limit(5).toList());
            } else {
                root.put("newsAvailability", "NOT_AVAILABLE");
                root.put("newsNote", "Bu analizde sembol bazlı güncel haber özeti bulunmuyor.");
            }
        }
        return writeJson(root);
    }

    /**
     * {@code toContextJson} — Snapshot'ı tam JSON context string'ine serileştirir.
     */
    public String toContextJson(PortfolioAiContextSnapshot snapshot) {
        return toCompactContextJson(snapshot);
    }

    /**
     * {@code isRealNewsItem} — Haber öğesinin gerçek (sentetik olmayan) haber olup olmadığını kontrol eder.
     */
    public static boolean isRealNewsItem(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
    Object headline = item.get("headline");
        if (headline != null && !headline.toString().isBlank()) {
            return true;
        }
        Object summary = item.get("shortNewsSummary");
        if (summary == null) {
            return false;
        }
        String s = summary.toString();
        return !s.isBlank() && !PLACEHOLDER_NEWS.equalsIgnoreCase(s.trim());
    }

    private Map<String, Object> compactAssetLine(PortfolioAiContextSnapshot.PositionLine line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", line.symbol());
        m.put("assetName", line.assetName());
        m.put("assetClass", line.assetClass());
        m.put("weightPct", line.weightPct());
        m.put("returnPct", line.returnPct());
        m.put("pnl", line.pnl());
        m.put("currentValue", line.currentValue());
        m.put("currentPrice", line.currentPrice());
        m.put("buyPrice", line.buyPrice());
        m.put("holdingDays", line.holdingDays());
        m.put("status", line.status());
        return m;
    }

    private String writeJson(Map<String, Object> root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Portfolio AI context serialization failed", ex);
        }
    }

    private PortfolioAiConcentrationLevel mapConcentration(PortfolioConcentrationRiskDto conc) {
        if (conc == null || conc.riskLevel() == null) {
            return PortfolioAiConcentrationLevel.MEDIUM;
        }
        return switch (conc.riskLevel().toUpperCase(Locale.ROOT)) {
            case "LOW" -> PortfolioAiConcentrationLevel.LOW;
            case "HIGH" -> PortfolioAiConcentrationLevel.HIGH;
            default -> PortfolioAiConcentrationLevel.MEDIUM;
        };
    }

    private Map<String, Object> loadMacroSummary() {
        Map<String, Object> macro = new LinkedHashMap<>();
        try {
            Map<String, Object> panel = marketDataClient.loadMacroPanelSummary();
            if (panel != null && !panel.isEmpty()) {
                macro.putAll(panel);
            }
        } catch (Exception ignored) {
            macro.put("macroAvailability", "NOT_AVAILABLE");
        }
        return macro;
    }
}
