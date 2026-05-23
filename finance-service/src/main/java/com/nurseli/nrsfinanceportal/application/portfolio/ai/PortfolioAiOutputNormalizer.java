package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * finance-service portfolio AI çıktı normalleştirici — parsed output alanlarını sanitize eder ve context ile hizalar.
 */
@Component

public class PortfolioAiOutputNormalizer {

    private final PortfolioAiAdviceSanitizer adviceSanitizer;

    /**
     * {@code PortfolioAiOutputNormalizer} — PortfolioAiAdviceSanitizer bağımlılığını enjekte eden public constructor.
     */
    public PortfolioAiOutputNormalizer(PortfolioAiAdviceSanitizer adviceSanitizer) {
        this.adviceSanitizer = adviceSanitizer;
    }
    /**
     * {@code sanitizeFields} — Parsed output'taki metin alanlarını tavsiye filtresinden geçirir.
     */
    public PortfolioAiParsedOutput sanitizeFields(PortfolioAiParsedOutput parsed) {
        return sanitizeAll(parsed);
    }

    /**
     * {@code normalize} — Context ile tutarlılık için parsed output alanlarını normalize eder ve eksikleri tamamlar.
     */
    public PortfolioAiParsedOutput normalize(PortfolioAiParsedOutput parsed, PortfolioAiContextSnapshot context) {
        PortfolioAiParsedOutput sanitized = sanitizeAll(parsed);
        List<PortfolioAiParsedOutput.AssetInsightParsed> insights =
                mergeInsights(sanitized.assetInsights(), sanitized.assetComments(), context);
    List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> assetComments =
                mergeAssetComments(sanitized.assetComments(), insights, context);

        PortfolioAiParsedOutput.PortfolioOverviewParsed overview =
                sanitized.portfolioOverview() != null
                        ? sanitized.portfolioOverview()
                        : overviewFromLegacy(sanitized, context);
        PortfolioAiParsedOutput.DecisionPerspectiveParsed decision =
                sanitized.decisionPerspective() != null
                        ? sanitized.decisionPerspective()
                        : decisionFromLegacy(sanitized, context);
        PortfolioAiParsedOutput.MacroAndNewsImpactParsed macroNews =
                sanitized.macroAndNewsImpact() != null
                        ? sanitized.macroAndNewsImpact()
                        : macroNewsFromContext(context);

        String summary = pickSummary(overview, sanitized.summary());
        List<String> findings = sanitized.findings() != null && !sanitized.findings().isEmpty()
                ? sanitized.findings()
                : List.of();
        String scenarioComment = sanitized.scenarioComment() != null ? sanitized.scenarioComment() : "";
        if (decision.comment() != null && !decision.comment().isBlank() && scenarioComment.isBlank()) {
            scenarioComment = decision.comment();
        }
        String disclaimer = sanitized.disclaimer() != null && !sanitized.disclaimer().isBlank()
                ? sanitized.disclaimer()
                : PortfolioAiPromptBuilder.DISCLAIMER;
        String finalNote = sanitized.finalNote() != null && !sanitized.finalNote().isBlank()
                ? sanitized.finalNote()
                : PortfolioAiPromptBuilder.FINAL_NOTE;

        return new PortfolioAiParsedOutput(
                parsed.portfolioScore(),
                parsed.riskScore(),
                parsed.confidence(),
                parsed.concentrationRisk(),
                summary,
                findings,
                scenarioComment,
                assetComments,
                disclaimer,
                parsed.source(),
                overview,
                decision,
                macroNews,
                insights,
                finalNote
        );
    }

    private PortfolioAiParsedOutput sanitizeAll(PortfolioAiParsedOutput parsed) {
        return new PortfolioAiParsedOutput(
                parsed.portfolioScore(),
                parsed.riskScore(),
                parsed.confidence(),
                parsed.concentrationRisk(),
                adviceSanitizer.sanitizeText(parsed.summary()),
                adviceSanitizer.sanitizeList(parsed.findings()),
                adviceSanitizer.sanitizeText(parsed.scenarioComment()),
                sanitizeAssetComments(parsed.assetComments()),
                adviceSanitizer.sanitizeText(parsed.disclaimer()),
                parsed.source(),
                sanitizeOverview(parsed.portfolioOverview()),
                sanitizeDecision(parsed.decisionPerspective()),
                sanitizeMacro(parsed.macroAndNewsImpact()),
                sanitizeInsights(parsed.assetInsights()),
                adviceSanitizer.sanitizeText(parsed.finalNote())
        );
    }

    private List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> sanitizeAssetComments(
            List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> items
    ) {
        if (items == null) {
            return List.of();
        }
        List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> out = new ArrayList<>();
        for (PortfolioAiParsedOutput.PortfolioAiParsedAssetComment a : items) {
            out.add(new PortfolioAiParsedOutput.PortfolioAiParsedAssetComment(
                    a.symbol(),
                    a.assetName(),
                    a.assetClass(),
                    a.weightPct(),
                    a.returnPct(),
                    a.assetScore(),
                    a.riskScore(),
                    a.riskLevel(),
                    adviceSanitizer.sanitizeText(a.role()),
                    adviceSanitizer.sanitizeList(a.positiveFactors()),
                    adviceSanitizer.sanitizeList(a.riskFactors()),
                    adviceSanitizer.sanitizeText(a.shortComment()),
                    adviceSanitizer.sanitizeText(a.detailComment())
            ));
        }
        return out;
    }

    private List<PortfolioAiParsedOutput.AssetInsightParsed> sanitizeInsights(
            List<PortfolioAiParsedOutput.AssetInsightParsed> items
    ) {
        if (items == null) {
            return List.of();
        }
        List<PortfolioAiParsedOutput.AssetInsightParsed> out = new ArrayList<>();
        for (PortfolioAiParsedOutput.AssetInsightParsed a : items) {
            out.add(new PortfolioAiParsedOutput.AssetInsightParsed(
                    a.symbol(),
                    a.assetName(),
                    a.assetClass(),
                    a.weightPct(),
                    a.returnPct(),
                    adviceSanitizer.sanitizeText(a.role()),
                    adviceSanitizer.sanitizeText(a.impactOnPortfolio()),
                    adviceSanitizer.sanitizeText(a.positiveView()),
                    adviceSanitizer.sanitizeText(a.riskView()),
                    adviceSanitizer.sanitizeList(a.whatToWatch()),
                    adviceSanitizer.sanitizeText(a.shortComment()),
                    adviceSanitizer.sanitizeText(a.detailComment())
            ));
        }
        return out;
    }

    private PortfolioAiParsedOutput.PortfolioOverviewParsed sanitizeOverview(
            PortfolioAiParsedOutput.PortfolioOverviewParsed o
    ) {
        if (o == null) {
            return null;
        }
        return new PortfolioAiParsedOutput.PortfolioOverviewParsed(
                adviceSanitizer.sanitizeText(o.summary()),
                adviceSanitizer.sanitizeText(o.currentSituation()),
                adviceSanitizer.sanitizeText(o.mainPositive()),
                adviceSanitizer.sanitizeText(o.mainRisk())
        );
    }

    private PortfolioAiParsedOutput.DecisionPerspectiveParsed sanitizeDecision(
            PortfolioAiParsedOutput.DecisionPerspectiveParsed d
    ) {
        if (d == null) {
            return null;
        }
        return new PortfolioAiParsedOutput.DecisionPerspectiveParsed(
                adviceSanitizer.sanitizeText(d.scenario()),
                adviceSanitizer.sanitizeText(d.comment()),
                adviceSanitizer.sanitizeText(d.shortTermView()),
                adviceSanitizer.sanitizeText(d.mediumTermView()),
                adviceSanitizer.sanitizeList(d.watchPoints())
        );
    }

    private PortfolioAiParsedOutput.MacroAndNewsImpactParsed sanitizeMacro(
            PortfolioAiParsedOutput.MacroAndNewsImpactParsed m
    ) {
        if (m == null) {
            return null;
        }
        return new PortfolioAiParsedOutput.MacroAndNewsImpactParsed(
                adviceSanitizer.sanitizeText(m.summary()),
                m.dataAvailability(),
                adviceSanitizer.sanitizeList(m.relevantItems())
        );
    }

    /**
     * {@code containsStillBanned} — Normalize edilmiş output'ta yasaklı ifade kalıp kalmadığını kontrol eder.
     */
    public boolean containsStillBanned(PortfolioAiParsedOutput parsed) {
        if (adviceSanitizer.containsStillBannedInTexts(
                parsed.summary(),
    parsed.scenarioComment(),
                parsed.finalNote()
        )) {
            return true;
        }
        if (parsed.findings() != null) {
            for (String f : parsed.findings()) {
                if (adviceSanitizer.containsStillBanned(f)) {
                    return true;
                }
            }
        }
        if (parsed.portfolioOverview() != null) {
            var o = parsed.portfolioOverview();
            if (adviceSanitizer.containsStillBannedInTexts(
                    o.summary(), o.currentSituation(), o.mainPositive(), o.mainRisk()
            )) {
                return true;
            }
        }
        if (parsed.decisionPerspective() != null) {
            var d = parsed.decisionPerspective();
            if (adviceSanitizer.containsStillBannedInTexts(
                    d.comment(), d.shortTermView(), d.mediumTermView()
            )) {
                return true;
            }
            if (d.watchPoints() != null) {
                for (String w : d.watchPoints()) {
                    if (adviceSanitizer.containsStillBanned(w)) {
                        return true;
                    }
                }
            }
        }
        for (PortfolioAiParsedOutput.PortfolioAiParsedAssetComment a : nullSafeAssets(parsed.assetComments())) {
            if (adviceSanitizer.containsStillBannedInTexts(a.shortComment(), a.detailComment(), a.role())) {
                return true;
            }
        }
        for (PortfolioAiParsedOutput.AssetInsightParsed a : nullSafeInsights(parsed.assetInsights())) {
            if (adviceSanitizer.containsStillBannedInTexts(
                    a.shortComment(), a.detailComment(), a.positiveView(), a.riskView(), a.impactOnPortfolio()
            )) {
                return true;
            }
        }
        return false;
    }

    private List<PortfolioAiParsedOutput.AssetInsightParsed> mergeInsights(
            List<PortfolioAiParsedOutput.AssetInsightParsed> insights,
            List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> comments,
            PortfolioAiContextSnapshot context
    ) {
        if (insights != null && !insights.isEmpty()) {
            return insights;
        }
        List<PortfolioAiParsedOutput.AssetInsightParsed> built = new ArrayList<>();
        for (PortfolioAiParsedOutput.PortfolioAiParsedAssetComment c : nullSafeAssets(comments)) {
            built.add(insightFromComment(c));
        }
        if (!built.isEmpty()) {
            return built;
        }
        for (PortfolioAiContextSnapshot.PositionLine line : context.assetCommentTargets()) {
            built.add(insightFromLine(line));
        }
        return built;
    }

    private List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> mergeAssetComments(
            List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> comments,
            List<PortfolioAiParsedOutput.AssetInsightParsed> insights,
            PortfolioAiContextSnapshot context
    ) {
        if (comments != null && !comments.isEmpty()) {
            return comments;
        }
        List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> built = new ArrayList<>();
        int i = 0;
        for (PortfolioAiParsedOutput.AssetInsightParsed ins : insights) {
            built.add(commentFromInsight(ins, context.healthScore(), i++));
        }
        return built;
    }

    private PortfolioAiParsedOutput.PortfolioOverviewParsed overviewFromLegacy(
            PortfolioAiParsedOutput parsed,
            PortfolioAiContextSnapshot context
    ) {
        String summary = parsed.summary() != null ? parsed.summary() : "";
        return new PortfolioAiParsedOutput.PortfolioOverviewParsed(
                summary,
                summary,
                parsed.findings() != null && !parsed.findings().isEmpty() ? parsed.findings().get(0) : "",
                context.concentrationLevel().name() + " yoğunlaşma profili"
        );
    }

    private PortfolioAiParsedOutput.DecisionPerspectiveParsed decisionFromLegacy(
            PortfolioAiParsedOutput parsed,
            PortfolioAiContextSnapshot context
    ) {
        return new PortfolioAiParsedOutput.DecisionPerspectiveParsed(
                scenarioLabel(context.analysisType()),
                parsed.scenarioComment() != null ? parsed.scenarioComment() : "",
                "",
                "",
                parsed.findings() != null ? parsed.findings() : List.of()
        );
    }

    private PortfolioAiParsedOutput.MacroAndNewsImpactParsed macroNewsFromContext(PortfolioAiContextSnapshot context) {
        String availability = context.includeNews() && context.newsAvailable()
                ? (context.newsSummaries().isEmpty() ? "PARTIAL" : "AVAILABLE")
                : "NOT_AVAILABLE";
        String summary = context.includeNews() && !context.newsAvailable()
                ? "Bu analizde sembol bazlı güncel haber özeti bulunmuyor."
                : (context.includeMacro() ? "Makro göstergeler portföy bağlamında izlenebilir." : "");
        return new PortfolioAiParsedOutput.MacroAndNewsImpactParsed(summary, availability, List.of());
    }

    private String pickSummary(
            PortfolioAiParsedOutput.PortfolioOverviewParsed overview,
            String legacySummary
    ) {
        if (overview != null && overview.summary() != null && !overview.summary().isBlank()) {
            return overview.summary();
        }
        return legacySummary != null ? legacySummary : "";
    }

    private PortfolioAiParsedOutput.AssetInsightParsed insightFromComment(
            PortfolioAiParsedOutput.PortfolioAiParsedAssetComment c
    ) {
        List<String> watch = new ArrayList<>();
        if (c.riskFactors() != null) {
            watch.addAll(c.riskFactors());
        }
        String positive = c.positiveFactors() != null && !c.positiveFactors().isEmpty()
                ? c.positiveFactors().get(0)
                : "";
        String risk = c.riskFactors() != null && !c.riskFactors().isEmpty() ? c.riskFactors().get(0) : "";
        return new PortfolioAiParsedOutput.AssetInsightParsed(
                c.symbol(),
                c.assetName(),
                c.assetClass(),
                c.weightPct(),
                c.returnPct(),
                c.role(),
                c.detailComment(),
                positive,
                risk,
                watch,
                c.shortComment(),
                c.detailComment()
        );
    }

    private PortfolioAiParsedOutput.AssetInsightParsed insightFromLine(PortfolioAiContextSnapshot.PositionLine line) {
        return new PortfolioAiParsedOutput.AssetInsightParsed(
                line.symbol(),
                line.assetName(),
                line.assetClass(),
                line.weightPct(),
                line.returnPct(),
                "",
                "",
                "",
                "",
                List.of(),
                "",
                ""
        );
    }

    private PortfolioAiParsedOutput.PortfolioAiParsedAssetComment commentFromInsight(
            PortfolioAiParsedOutput.AssetInsightParsed ins,
            int healthScore,
            int index
    ) {
        int assetScore = Math.min(100, Math.max(35, healthScore + (index % 7) - 3));
        int riskScore = ins.weightPct() >= 25 ? 58 : 40;
        String riskLevel = riskScore >= 55 ? "MEDIUM" : "LOW";
        List<String> positives = ins.positiveView() != null && !ins.positiveView().isBlank()
                ? List.of(ins.positiveView())
                : List.of();
        List<String> risks = ins.riskView() != null && !ins.riskView().isBlank()
                ? List.of(ins.riskView())
                : (ins.whatToWatch() != null ? ins.whatToWatch() : List.of());
        return new PortfolioAiParsedOutput.PortfolioAiParsedAssetComment(
                ins.symbol(),
                ins.assetName(),
                ins.assetClass(),
                ins.weightPct(),
                ins.returnPct(),
                assetScore,
                riskScore,
                riskLevel,
                ins.role() != null ? ins.role() : "",
                positives,
                risks,
                ins.shortComment() != null ? ins.shortComment() : "",
                ins.detailComment() != null ? ins.detailComment() : ins.shortComment()
        );
    }

    private String scenarioLabel(PortfolioAiAnalysisType type) {
        return switch (type) {
            case ONE_WEEK_HOLD -> "1 hafta tutma senaryosu";
            case ONE_MONTH_HOLD -> "1 ay tutma senaryosu";
            case SELL_SCENARIO -> "Satış senaryosu";
            default -> "Genel portföy değerlendirmesi";
        };
    }

    private List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> nullSafeAssets(
            List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> items
    ) {
        return items != null ? items : List.of();
    }

    private List<PortfolioAiParsedOutput.AssetInsightParsed> nullSafeInsights(
            List<PortfolioAiParsedOutput.AssetInsightParsed> items
    ) {
        return items != null ? items : List.of();
    }
}
