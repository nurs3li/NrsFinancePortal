package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConfidenceLevel;

import java.util.List;

/**
 * finance-service portfolio AI ayrıştırılmış çıktı kaydı — sanitize edilmiş AI analiz bölümlerini taşır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public record PortfolioAiParsedOutput(
        int portfolioScore,
@JsonIgnoreProperties(ignoreUnknown = true)
        int riskScore,
        PortfolioAiConfidenceLevel confidence,
        PortfolioAiConcentrationLevel concentrationRisk,
        String summary,
        List<String> findings,
        String scenarioComment,
        List<PortfolioAiParsedAssetComment> assetComments,
        String disclaimer,
        String source,
        PortfolioOverviewParsed portfolioOverview,
        DecisionPerspectiveParsed decisionPerspective,
        MacroAndNewsImpactParsed macroAndNewsImpact,
        List<AssetInsightParsed> assetInsights,
        String finalNote
) {
    /**
     * {@code withSource} — Kaynak alanını değiştirilmiş yeni ParsedOutput kopyası döner.
     */
    public PortfolioAiParsedOutput withSource(String newSource) {
        return new PortfolioAiParsedOutput(
                portfolioScore,
    riskScore,
                confidence,
                concentrationRisk,
                summary,
                findings,
                scenarioComment,
                assetComments,
                disclaimer,
                newSource,
                portfolioOverview,
                decisionPerspective,
                macroAndNewsImpact,
                assetInsights,
                finalNote
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * PortfolioOverviewParsed — Portfolio AI genel bakış bölümü — özet, risk ve çeşitlilik metinleri.
     */
    public record PortfolioOverviewParsed(
            String summary,
            String currentSituation,
            String mainPositive,
            String mainRisk
    ) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * DecisionPerspectiveParsed — Portfolio AI karar perspektifi bölümü — senaryo ve dikkat noktaları.
     */
    public record DecisionPerspectiveParsed(
            String scenario,
            String comment,
            String shortTermView,
            String mediumTermView,
            List<String> watchPoints
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * MacroAndNewsImpactParsed — Portfolio AI makro ve haber etkisi bölümü.
     */
    public record MacroAndNewsImpactParsed(
            String summary,
            String dataAvailability,
            List<String> relevantItems
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * AssetInsightParsed — Tek varlık AI insight satırı — sembol ve yorum metni.
     */
    public record AssetInsightParsed(
            String symbol,
            String assetName,
            String assetClass,
            double weightPct,
            Double returnPct,
            String role,
            String impactOnPortfolio,
            String positiveView,
            String riskView,
            List<String> whatToWatch,
            String shortComment,
    String detailComment
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * PortfolioAiParsedAssetComment — Varlık yorumu kaydı — sembol, başlık ve açıklama.
     */
    public record PortfolioAiParsedAssetComment(
            String symbol,
            String assetName,
            String assetClass,
            double weightPct,
            Double returnPct,
            int assetScore,
            int riskScore,
            String riskLevel,
            String role,
            List<String> positiveFactors,
            List<String> riskFactors,
            String shortComment,
            String detailComment
    ) {
    }
}
