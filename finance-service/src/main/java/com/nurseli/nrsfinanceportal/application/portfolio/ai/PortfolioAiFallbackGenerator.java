package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConfidenceLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * finance-service portfolio AI fallback Ã¼retici â€” OpenAI kullanÄ±lamadÄ±ÄŸÄ±nda kural tabanlÄ± analiz Ã§Ä±ktÄ±sÄ± Ã¼retir.
 */
@Component

public class PortfolioAiFallbackGenerator {

    /**
     * {@code build} â€” Context'ten kural tabanlÄ± PortfolioAiParsedOutput fallback analizi oluÅŸturur.
     */
    public PortfolioAiParsedOutput build(PortfolioAiContextSnapshot context) {
        int portfolioScore = Math.min(100, Math.max(35, context.healthScore()));
        int riskScore = context.concentrationLevel() == PortfolioAiConcentrationLevel.HIGH ? 68 : 45;
    String largest = context.largestPositionSymbol();
        List<String> findings = buildFindings(context);
        String summary = buildPortfolioSummary(context);
        String scenarioComment = buildScenarioComment(context);
        List<PortfolioAiParsedOutput.AssetInsightParsed> insights = buildAssetInsights(context);
        List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> assetComments =
                insights.stream().map(ins -> commentFromInsight(ins, context.healthScore())).toList();

        PortfolioAiParsedOutput.PortfolioOverviewParsed overview = new PortfolioAiParsedOutput.PortfolioOverviewParsed(
                summary,
                buildCurrentSituation(context),
                buildMainPositive(context),
                buildMainRisk(context)
        );
        PortfolioAiParsedOutput.DecisionPerspectiveParsed decision =
                new PortfolioAiParsedOutput.DecisionPerspectiveParsed(
                        scenarioLabel(context.analysisType()),
                        scenarioComment,
                        buildShortTermView(context),
                        "",
                        buildWatchPoints(context)
                );
        PortfolioAiParsedOutput.MacroAndNewsImpactParsed macroNews = buildMacroNews(context);

        return new PortfolioAiParsedOutput(
                portfolioScore,
                riskScore,
                PortfolioAiConfidenceLevel.MEDIUM,
                context.concentrationLevel(),
                summary,
                findings,
                scenarioComment,
                assetComments,
                PortfolioAiPromptBuilder.DISCLAIMER,
                null,
                overview,
                decision,
                macroNews,
                insights,
                PortfolioAiPromptBuilder.FINAL_NOTE
        );
    }

    /**
     * {@code buildAssetComments} â€” SeÃ§ili pozisyonlar iÃ§in kural tabanlÄ± varlÄ±k yorumlarÄ± Ã¼retir.
     */
    public List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> buildAssetComments(
            PortfolioAiContextSnapshot context
    ) {
    return buildAssetInsights(context).stream()
                .map(ins -> commentFromInsight(ins, context.healthScore()))
                .toList();
    }

    /**
     * {@code mergeWithTargets} â€” Hedef pozisyon listesiyle varlÄ±k yorumlarÄ±nÄ± birleÅŸtirir ve eksikleri tamamlar.
     */
    public List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> mergeWithTargets(
            List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> existing,
            PortfolioAiContextSnapshot context
    ) {
        Map<String, PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> bySym = new LinkedHashMap<>();
        if (existing != null) {
            for (PortfolioAiParsedOutput.PortfolioAiParsedAssetComment c : existing) {
                if (c != null && c.symbol() != null && !isMechanicalComment(c)) {
                    bySym.put(c.symbol().trim().toUpperCase(Locale.ROOT), c);
                }
            }
        }
        for (PortfolioAiParsedOutput.PortfolioAiParsedAssetComment generated : buildAssetComments(context)) {
            String sym = generated.symbol().trim().toUpperCase(Locale.ROOT);
            PortfolioAiParsedOutput.PortfolioAiParsedAssetComment cur = bySym.get(sym);
            if (cur == null || isMechanicalComment(cur)) {
                bySym.put(sym, generated);
            }
        }
        List<PortfolioAiParsedOutput.PortfolioAiParsedAssetComment> ordered = new ArrayList<>();
        for (PortfolioAiContextSnapshot.PositionLine line : context.assetCommentTargets()) {
            PortfolioAiParsedOutput.PortfolioAiParsedAssetComment c =
                    bySym.get(line.symbol().trim().toUpperCase(Locale.ROOT));
            if (c != null) {
                ordered.add(c);
            }
        }
        return ordered.isEmpty() ? (existing != null ? existing : List.of()) : ordered;
    }

    private boolean isMechanicalComment(PortfolioAiParsedOutput.PortfolioAiParsedAssetComment c) {
        String detail = c.detailComment() != null ? c.detailComment().toLowerCase(Locale.ROOT) : "";
        String shortC = c.shortComment() != null ? c.shortComment().toLowerCase(Locale.ROOT) : "";
        return detail.contains(PortfolioAiAdviceSanitizer.MECHANICAL_PHRASE)
                || shortC.contains(PortfolioAiAdviceSanitizer.MECHANICAL_PHRASE);
    }

    private List<PortfolioAiParsedOutput.AssetInsightParsed> buildAssetInsights(PortfolioAiContextSnapshot context) {
        List<PortfolioAiParsedOutput.AssetInsightParsed> out = new ArrayList<>();
        boolean first = true;
        for (PortfolioAiContextSnapshot.PositionLine line : context.assetCommentTargets()) {
            out.add(insightForLine(line, first, context));
            first = false;
        }
        return out;
    }

    private PortfolioAiParsedOutput.AssetInsightParsed insightForLine(
            PortfolioAiContextSnapshot.PositionLine line,
            boolean primary,
            PortfolioAiContextSnapshot context
    ) {
        String sym = line.symbol();
        double w = line.weightPct();
        Double ret = line.returnPct();
        String role = roleFor(line, primary);
        String impact = impactFor(sym, w, context.largestPositionSymbol());
        String positive = positiveFor(line, w, ret);
        String risk = riskFor(line, w, ret, context.concentrationLevel());
        List<String> watch = watchFor(line, w, ret);
        String shortComment = shortCommentFor(sym, role, w, context.largestPositionSymbol());
        String detailComment = detailCommentFor(sym, line, w, ret, role, context);
        return new PortfolioAiParsedOutput.AssetInsightParsed(
                sym,
                line.assetName(),
                line.assetClass(),
                w,
                ret,
                role,
                impact,
                positive,
                risk,
                watch,
                shortComment,
                detailComment
        );
    }

    private PortfolioAiParsedOutput.PortfolioAiParsedAssetComment commentFromInsight(
            PortfolioAiParsedOutput.AssetInsightParsed ins,
            int healthScore
    ) {
        int riskScore = ins.weightPct() >= 25 ? 62 : 42;
        return new PortfolioAiParsedOutput.PortfolioAiParsedAssetComment(
                ins.symbol(),
                ins.assetName(),
                ins.assetClass(),
                ins.weightPct(),
                ins.returnPct(),
                Math.min(100, Math.max(40, healthScore)),
                riskScore,
                riskScore >= 55 ? "MEDIUM" : "LOW",
                ins.role(),
                ins.positiveView() != null && !ins.positiveView().isBlank()
                        ? List.of(ins.positiveView())
                        : List.of(),
                ins.riskView() != null && !ins.riskView().isBlank()
                        ? List.of(ins.riskView())
                        : List.of(),
                ins.shortComment(),
                ins.detailComment()
        );
    }

    private String buildPortfolioSummary(PortfolioAiContextSnapshot context) {
        List<String> parts = new ArrayList<>();
        if (context.realReturnAvailable() && context.realReturnPct() != null && context.realReturnPct() > 0) {
            parts.add(
                    "Portföy reel olarak pozitif getiri üretmiş görünüyor. Bu, enflasyon etkisi dikkate alındığında "
                            + "portföyün değer koruma tarafında olumlu sinyal verdiğini gösterir."
            );
        }
        if (context.concentrationLevel() == PortfolioAiConcentrationLevel.HIGH
                && context.largestPositionSymbol() != null) {
            parts.add(
                    "Portföyde yoğunlaşma riski yüksek. Özellikle "
                            + context.largestPositionSymbol()
                            + " ağırlığı toplam portföy sonucunu belirgin şekilde etkileyebilir."
            );
        }
        if (context.nominalReturnPct() != null && context.nominalReturnPct() > 0) {
            parts.add("Nominal getiri pozitif; ancak dağılım ve en büyük pozisyon etkisi birlikte izlenmeli.");
        }
        if (parts.isEmpty()) {
            parts.add(
                    "Portföy yapısı, dağılım ve getiri profili mevcut verilerle karar destek amaçlı özetlenmiştir."
            );
        }
        return String.join(" ", parts);
    }

    private String buildCurrentSituation(PortfolioAiContextSnapshot context) {
        String largest = context.largestPositionSymbol();
        if (largest != null) {
            return String.format(
                    Locale.ROOT,
                    "Açık pozisyon sayısı %d; en büyük ağırlık %s (%%%.1f).",
                    context.openCount(),
                    largest,
                    context.largestPositionWeightPct()
            );
        }
        return "Açık pozisyonlar mevcut portföy değerinin dağılımını belirliyor.";
    }

    private String buildMainPositive(PortfolioAiContextSnapshot context) {
        if (context.realReturnAvailable() && context.realReturnPct() != null && context.realReturnPct() > 0) {
            return "Reel getiri pozitif görünüyor; enflasyon üzerinde değer koruma sinyali var.";
        }
        if (context.nominalReturnPct() != null && context.nominalReturnPct() > 0) {
            return "Nominal getiri pozitif; portföy büyüme yönünde hareket etmiş görünüyor.";
        }
        return "Dağılım çeşitliliği portföyü tek bir kaynağa tamamen bağımlı kılmayabilir.";
    }

    private String buildMainRisk(PortfolioAiContextSnapshot context) {
        if (context.concentrationLevel() == PortfolioAiConcentrationLevel.HIGH) {
            return "Yoğunlaşma riski yüksek; tek veya birkaç varlık toplam sonucu belirleyebilir.";
        }
        if (context.largestPositionWeightPct() >= 40 && context.largestPositionSymbol() != null) {
            return context.largestPositionSymbol()
                    + " yüksek ağırlıkla portföy sonucuna duyarlılık yaratıyor.";
        }
        return "Kısa vadede piyasa oynaklığı ve makro koşullar dalgalanmayı artırabilir.";
    }

    private List<String> buildFindings(PortfolioAiContextSnapshot context) {
        List<String> findings = new ArrayList<>();
        if (context.realReturnAvailable() && context.realReturnPct() != null) {
            findings.add("Reel getiri bağlamı değerlendirmeye dahil edildi.");
        }
        if (context.largestPositionSymbol() != null) {
            findings.add(
                    "En büyük pozisyon ("
                            + context.largestPositionSymbol()
                            + ") portföy sonucuna belirgin etki edebilir."
            );
        }
        if (context.includeNews() && !context.newsAvailable()) {
            findings.add("Güncel sembol bazlı haber özeti bu analizde bulunmuyor.");
        }
        if (findings.isEmpty()) {
            findings.add("Portföy dağılımı ve getiri profili özetlendi.");
        }
        return findings;
    }

    private String buildScenarioComment(PortfolioAiContextSnapshot context) {
        String largest = context.largestPositionSymbol();
        return switch (context.analysisType()) {
            case SELL_SCENARIO -> largest != null
                    ? "Kısa vadeli satış düşünülüyorsa, portföy sonucunu en çok "
                    + largest
                    + " ağırlığının etkilediği dikkate alınmalı."
                    : "Satış senaryosunda dağılım ve yoğunlaşma birlikte değerlendirilmeli.";
            case ONE_WEEK_HOLD, ONE_MONTH_HOLD -> "Tutma senaryosunda yoğunlaşma, reel/nominal getiri ve kısa vadeli "
                    + "oynaklık birlikte izlenmeli.";
            default -> "Genel değerlendirmede dağılım, getiri ve yoğunlaşma profili öne çıkar.";
        };
    }

    private String buildShortTermView(PortfolioAiContextSnapshot context) {
        if (context.includeNews() && !context.newsAvailable()) {
            return "Kısa vadede haber akışı ve makro koşullar oynaklığı artırabilir.";
        }
        return "Kısa vadede en büyük pozisyonların fiyat hareketleri portföy sonucunu etkileyebilir.";
    }

    private List<String> buildWatchPoints(PortfolioAiContextSnapshot context) {
        List<String> points = new ArrayList<>();
        if (context.concentrationLevel() == PortfolioAiConcentrationLevel.HIGH) {
            points.add("Yoğunlaşma riski ve en büyük pozisyon ağırlığı");
        }
        if (context.realReturnAvailable()) {
            points.add("Reel getiri ve enflasyon etkisi");
        }
        if (context.includeMacro()) {
            points.add("Politika faizi ve enflasyon göstergeleri");
        }
        if (points.isEmpty()) {
            points.add("Dağılım değişimi ve büyük pozisyon hareketleri");
        }
        return points;
    }

    private PortfolioAiParsedOutput.MacroAndNewsImpactParsed buildMacroNews(PortfolioAiContextSnapshot context) {
        if (!context.includeNews() && !context.includeMacro()) {
            return new PortfolioAiParsedOutput.MacroAndNewsImpactParsed("", "NOT_AVAILABLE", List.of());
        }
        List<String> items = new ArrayList<>();
        String summary;
        String availability;
        if (context.includeNews() && !context.newsAvailable()) {
            summary = "Bu analizde sembol bazlı güncel haber özeti bulunmuyor.";
            availability = "NOT_AVAILABLE";
        } else if (context.includeMacro() && context.macroSummary() != null && !context.macroSummary().isEmpty()) {
            summary = "Makro göstergeler (enflasyon, politika faizi) portföy reel getiri bağlamında izlenebilir.";
            availability = "PARTIAL";
            items.add("Faiz ve enflasyon paneli özetlendi.");
        } else {
            summary = "Makro ve haber katmanı bu analizde sınırlı.";
            availability = "NOT_AVAILABLE";
        }
        return new PortfolioAiParsedOutput.MacroAndNewsImpactParsed(summary, availability, items);
    }

    private String roleFor(PortfolioAiContextSnapshot.PositionLine line, boolean primary) {
        if (primary || line.weightPct() >= 40) {
            return "Portföyün ana taşıyıcı varlığı";
        }
        if (line.weightPct() >= 15) {
            return "Portföyde önemli ağırlığa sahip varlık";
        }
        return "Tamamlayıcı varlık";
    }

    private String impactFor(String sym, double weight, String largestSym) {
        if (largestSym != null && largestSym.equalsIgnoreCase(sym) && weight >= 25) {
            return sym + " portföy sonucunu belirgin şekilde etkileyebilir.";
        }
        if (weight >= 15) {
            return "Bu varlıktaki hareketler portföy sonucuna anlamlı katkı yapabilir.";
        }
        return "Sınırlı ağırlıkla portföye destekleyici etki sağlayabilir.";
    }

    private String positiveFor(PortfolioAiContextSnapshot.PositionLine line, double w, Double ret) {
        if (ret != null && ret > 10) {
            return line.symbol() + " pozitif katkı sağlamış görünüyor.";
        }
        if (w >= 25) {
            return "Yüksek ağırlıkla portföy yapısında merkezi konumda.";
        }
        return "Portföy çeşitlendirmesine katkı sağlayabilir.";
    }

    private String riskFor(
            PortfolioAiContextSnapshot.PositionLine line,
            double w,
            Double ret,
            PortfolioAiConcentrationLevel concentration
    ) {
        if (ret != null && ret < 0) {
            return line.symbol() + " negatif getiri üretmiş görünüyor; kısa vadede oynaklık izlenmeli.";
        }
        if (w >= 40 || (concentration == PortfolioAiConcentrationLevel.HIGH && w >= 25)) {
            return "Yüksek ağırlık tek varlık yoğunlaşması riski oluşturabilir.";
        }
        String cls = line.assetClass() != null ? line.assetClass().toUpperCase(Locale.ROOT) : "";
        if (cls.contains("CRYPTO")) {
            return "Kripto varlıklar yüksek oynaklık taşıdığı için ağırlık ve kısa vadeli hareketler izlenmelidir.";
        }
        return "Fiyat ve haber akışı kısa vadede dalgalanma yaratabilir.";
    }

    private List<String> watchFor(PortfolioAiContextSnapshot.PositionLine line, double w, Double ret) {
        List<String> watch = new ArrayList<>();
        if (ret != null && ret < 0) {
            watch.add("Fiyat oynaklığı ve haber akışı");
        }
        if (w >= 25) {
            watch.add("Ağırlık değişimi ve portföy dengesi");
        }
        String cls = line.assetClass() != null ? line.assetClass().toUpperCase(Locale.ROOT) : "";
        if (cls.contains("FUND") || cls.contains("ETF")) {
            watch.add("Piyasa yönüne bağımlılık");
        } else if (cls.contains("STOCK")) {
            watch.add("Şirket ve sektör haberleri");
        }
        if (watch.isEmpty()) {
            watch.add("Getiri ve ağırlık trendi");
        }
        return watch;
    }

    private String shortCommentFor(String sym, String role, double w, String largestSym) {
        if (w >= 40) {
            return sym
                    + " portföyün ana taşıyıcı varlığı konumunda. Ağırlığı yüksek olduğu için toplam portföy "
                    + "performansını belirgin şekilde etkileyebilir.";
        }
        if (largestSym != null && largestSym.equalsIgnoreCase(sym)) {
            return sym + " " + role.toLowerCase(Locale.ROOT) + "; portföy sonucuna belirgin etki edebilir.";
        }
        if (w >= 15) {
            return sym
                    + " portföyde önemli ağırlığa sahip. Bu varlıktaki hareketler portföy sonucuna anlamlı katkı yapabilir.";
        }
        return sym + " portföyde tamamlayıcı rol oynuyor; ağırlık sınırlı.";
    }

    private String detailCommentFor(
            String sym,
            PortfolioAiContextSnapshot.PositionLine line,
            double w,
            Double ret,
            String role,
            PortfolioAiContextSnapshot context
    ) {
        StringBuilder sb = new StringBuilder(shortCommentFor(sym, role, w, context.largestPositionSymbol()));
        String cls = line.assetClass() != null ? line.assetClass().toUpperCase(Locale.ROOT) : "";
        if (cls.contains("FUND") || cls.contains("ETF")) {
            sb.append(" Fon/ETF yapısı çeşitlendirme sağlayabilir; ancak ağırlık yüksekse piyasa yönüne bağımlılık artabilir.");
        } else if (cls.contains("STOCK")) {
            sb.append(" Hisse pozisyonları şirket ve sektör haberlerine daha duyarlı olabilir.");
        } else if (cls.contains("CRYPTO")) {
            sb.append(" Kripto varlıklar yüksek oynaklık taşıdığı için ağırlık ayrıca izlenmelidir.");
        }
        if (ret != null && ret > 10) {
            sb.append(" Geçmiş getiri olumludur; ancak gelecek performans için tek başına yeterli gösterge değildir.");
        }
        return sb.toString().trim();
    }

    private String scenarioLabel(PortfolioAiAnalysisType type) {
        return switch (type) {
            case ONE_WEEK_HOLD -> "1 hafta tutma senaryosu";
            case ONE_MONTH_HOLD -> "1 ay tutma senaryosu";
            case SELL_SCENARIO -> "Satış senaryosu";
            default -> "Genel portföy değerlendirmesi";
        };
    }
}
