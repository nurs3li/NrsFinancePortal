package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConfidenceLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * finance-service portfolio AI yanıt temizleyici — OpenAI ham JSON'unu parse eder, sanitize eder ve reddederse fallback tetikler.
 */
@Component

public class PortfolioAiResponseSanitizer {

    private final ObjectMapper objectMapper;
    private final PortfolioAiFallbackGenerator fallbackGenerator;
    private final PortfolioAiAdviceSanitizer adviceSanitizer;
    private final PortfolioAiOutputNormalizer outputNormalizer;

    /**
     * {@code PortfolioAiResponseSanitizer} — ObjectMapper ve sanitizer bağımlılıklarını enjekte eden public constructor.
     */
    public PortfolioAiResponseSanitizer(
            ObjectMapper objectMapper,
            PortfolioAiFallbackGenerator fallbackGenerator,
    PortfolioAiAdviceSanitizer adviceSanitizer,
            PortfolioAiOutputNormalizer outputNormalizer
    ) {
        this.objectMapper = objectMapper;
        this.fallbackGenerator = fallbackGenerator;
        this.adviceSanitizer = adviceSanitizer;
        this.outputNormalizer = outputNormalizer;
    }

    /**
     * {@code parseAndSanitize} — Ham model JSON'unu parse eder, alanları temizler/normalize eder; yasaklı içerik varsa reddeder.
     */
    public PortfolioAiParsedOutput parseAndSanitize(
            String rawJson,
            PortfolioAiContextSnapshot context,
    boolean adviceDetectedInRaw
    ) {
        if (adviceDetectedInRaw) {
            return taggedFallback(context, OpenAiFailureReason.SANITIZER_REJECTED);
        }
        PortfolioAiParsedOutput parsed;
        try {
            PortfolioAiRawModel raw = objectMapper.readValue(rawJson, PortfolioAiRawModel.class);
            parsed = mapRaw(raw);
        } catch (Exception ex) {
            return taggedFallback(context, OpenAiFailureReason.PARSE_ERROR);
        }
        parsed = outputNormalizer.sanitizeFields(parsed);
        if (outputNormalizer.containsStillBanned(parsed)) {
            return taggedFallback(context, OpenAiFailureReason.SANITIZER_REJECTED);
        }
        return finalizeOutput(parsed, context);
    }

    /**
     * {@code containsAdviceText} — Metinde yatırım tavsiyesi kalıbı olup olmadığını kontrol eder.
     */
    public boolean containsAdviceText(String text) {
        return adviceSanitizer.containsStillBanned(text);
    }

    private PortfolioAiParsedOutput mapRaw(PortfolioAiRawModel raw) {
        List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> assets = new ArrayList<>();
        if (raw.assetComments() != null) {
            for (PortfolioAiRawModel.PortfolioAiRawAsset a : raw.assetComments()) {
                if (a == null || a.symbol() == null || a.symbol().isBlank()) {
                    continue;
                }
                assets.add(new PortfolioAiParsedOutput.PortfolioAiParsedAssetComment(
                        a.symbol().trim().toUpperCase(Locale.ROOT),
                        blankToNull(a.assetName()),
                        blankToNull(a.assetClass()),
                        a.weightPct() != null ? a.weightPct() : 0,
                        a.returnPct(),
                        clampScore(a.assetScore(), 50),
                        clampScore(a.riskScore(), 50),
                        normalizeRiskLevel(a.riskLevel()),
                        blankToEmpty(a.role()),
                        nullToEmptyList(a.positiveFactors()),
                        nullToEmptyList(a.riskFactors()),
                        blankToEmpty(a.shortComment()),
                        blankToEmpty(a.detailComment())
                ));
            }
        }
        List<PortfolioAiParsedOutput.AssetInsightParsed> insights = new ArrayList<>();
        if (raw.assetInsights() != null) {
            for (PortfolioAiRawModel.PortfolioAiRawAssetInsight a : raw.assetInsights()) {
                if (a == null || a.symbol() == null || a.symbol().isBlank()) {
                    continue;
                }
                insights.add(new PortfolioAiParsedOutput.AssetInsightParsed(
                        a.symbol().trim().toUpperCase(Locale.ROOT),
                        blankToNull(a.assetName()),
                        blankToNull(a.assetClass()),
                        a.weightPct() != null ? a.weightPct() : 0,
                        a.returnPct(),
                        blankToEmpty(a.role()),
                        blankToEmpty(a.impactOnPortfolio()),
                        blankToEmpty(a.positiveView()),
                        blankToEmpty(a.riskView()),
                        nullToEmptyList(a.whatToWatch()),
                        blankToEmpty(a.shortComment()),
                        blankToEmpty(a.detailComment())
                ));
            }
        }
        return new PortfolioAiParsedOutput(
                clampScore(raw.portfolioScore(), 60),
                clampScore(raw.riskScore(), 50),
                parseConfidence(raw.confidence()),
                parseConcentration(raw.concentrationRisk()),
                blankToEmpty(raw.summary()),
                nullToEmptyList(raw.findings()),
                blankToEmpty(raw.scenarioComment()),
                assets,
                blankToEmpty(raw.disclaimer()),
                null,
                mapOverview(raw.portfolioOverview()),
                mapDecision(raw.decisionPerspective()),
                mapMacro(raw.macroAndNewsImpact()),
                insights,
                blankToEmpty(raw.finalNote())
        );
    }

    private PortfolioAiParsedOutput.PortfolioOverviewParsed mapOverview(PortfolioAiRawModel.PortfolioAiRawOverview o) {
        if (o == null) {
            return null;
        }
        return new PortfolioAiParsedOutput.PortfolioOverviewParsed(
                blankToEmpty(o.summary()),
                blankToEmpty(o.currentSituation()),
                blankToEmpty(o.mainPositive()),
                blankToEmpty(o.mainRisk())
        );
    }

    private PortfolioAiParsedOutput.DecisionPerspectiveParsed mapDecision(PortfolioAiRawModel.PortfolioAiRawDecision d) {
        if (d == null) {
            return null;
        }
        return new PortfolioAiParsedOutput.DecisionPerspectiveParsed(
                blankToEmpty(d.scenario()),
                blankToEmpty(d.comment()),
                blankToEmpty(d.shortTermView()),
                blankToEmpty(d.mediumTermView()),
                nullToEmptyList(d.watchPoints())
        );
    }

    private PortfolioAiParsedOutput.MacroAndNewsImpactParsed mapMacro(PortfolioAiRawModel.PortfolioAiRawMacroNews m) {
        if (m == null) {
            return null;
        }
        return new PortfolioAiParsedOutput.MacroAndNewsImpactParsed(
                blankToEmpty(m.summary()),
                m.dataAvailability() != null ? m.dataAvailability() : "NOT_AVAILABLE",
                nullToEmptyList(m.relevantItems())
        );
    }

    private PortfolioAiParsedOutput taggedFallback(
            PortfolioAiContextSnapshot context,
            @SuppressWarnings("unused") OpenAiFailureReason reason
    ) {
        return fallbackGenerator.build(context).withSource(PortfolioAiModels.SOURCE_FALLBACK);
    }

    private PortfolioAiParsedOutput finalizeOutput(PortfolioAiParsedOutput parsed, PortfolioAiContextSnapshot context) {
        List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> assets = parsed.assetComments();
        if (assets == null || assets.isEmpty()) {
            assets = fallbackGenerator.buildAssetComments(context);
        } else {
            assets = fallbackGenerator.mergeWithTargets(assets, context);
        }
        PortfolioAiParsedOutput draft = new PortfolioAiParsedOutput(
                clampScore(parsed.portfolioScore(), context.healthScore()),
                clampScore(parsed.riskScore(), 55),
                parsed.confidence() != null ? parsed.confidence() : PortfolioAiConfidenceLevel.MEDIUM,
                parsed.concentrationRisk() != null ? parsed.concentrationRisk() : context.concentrationLevel(),
                parsed.summary(),
                parsed.findings(),
                parsed.scenarioComment(),
                assets,
                parsed.disclaimer() != null && !parsed.disclaimer().isBlank()
                        ? parsed.disclaimer()
                        : PortfolioAiPromptBuilder.DISCLAIMER,
                parsed.source(),
                parsed.portfolioOverview(),
                parsed.decisionPerspective(),
                parsed.macroAndNewsImpact(),
                parsed.assetInsights(),
                parsed.finalNote() != null && !parsed.finalNote().isBlank()
                        ? parsed.finalNote()
                        : PortfolioAiPromptBuilder.FINAL_NOTE
        );
        return outputNormalizer.normalize(draft, context);
    }

    private int clampScore(Integer value, int fallback) {
        if (value == null) {
            return Math.min(100, Math.max(0, fallback));
        }
        return Math.min(100, Math.max(0, value));
    }

    private PortfolioAiConfidenceLevel parseConfidence(String value) {
        if (value == null) {
            return PortfolioAiConfidenceLevel.MEDIUM;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> PortfolioAiConfidenceLevel.LOW;
            case "HIGH" -> PortfolioAiConfidenceLevel.HIGH;
            default -> PortfolioAiConfidenceLevel.MEDIUM;
        };
    }

    private PortfolioAiConcentrationLevel parseConcentration(String value) {
        if (value == null) {
            return PortfolioAiConcentrationLevel.MEDIUM;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> PortfolioAiConcentrationLevel.LOW;
            case "HIGH" -> PortfolioAiConcentrationLevel.HIGH;
            default -> PortfolioAiConcentrationLevel.MEDIUM;
        };
    }

    private String normalizeRiskLevel(String value) {
        if (value == null || value.isBlank()) {
            return "MEDIUM";
        }
        String u = value.trim().toUpperCase(Locale.ROOT);
        if (u.equals("LOW") || u.equals("HIGH")) {
            return u;
        }
        return "MEDIUM";
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private List<String> nullToEmptyList(List<String> items) {
        return items != null ? items : List.of();
    }
}
