package com.nurseli.whaleanalytics.domain.investor;

import com.nurseli.whaleanalytics.config.InvestorBehaviorAnalysisProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class InvestorBehaviorExplanationBuilder {

    private InvestorBehaviorExplanationBuilder() {
    }

    public static List<String> build(
            PortfolioExposureSummary summary,
            PortfolioImpactBreakdown breakdown,
            InvestorLevel level,
            InvestorBehaviorAnalysisProperties properties
    ) {
        InvestorBehaviorAnalysisProperties.Thresholds thresholds = properties.getThresholds();
        List<String> out = new ArrayList<>();
        BigDecimal total = summary.totalPortfolioValueTry();
        if (total != null && total.compareTo(thresholds.getL2PortfolioTry()) >= 0) {
            out.add("Toplam portföy değeri L2 eşiğini aşıyor.");
        }
        if (summary.largestPositionSymbol() != null && summary.largestPositionRatio() != null) {
            BigDecimal pct = summary.largestPositionRatio().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            out.add(summary.largestPositionSymbol() + " pozisyonu portföyün %" + pct + "'ünü oluşturuyor.");
        }
        BigDecimal nom = summary.totalNominalProfitTry();
        BigDecimal real = summary.totalRealProfitTry();
        if (nom != null && nom.signum() > 0 && real != null && real.signum() < 0) {
            out.add("Nominal kâr pozitif ancak reel kâr enflasyon sonrası düşük.");
        }
        if (summary.cryptoExposureRatio() != null
                && summary.cryptoExposureRatio().compareTo(new BigDecimal("0.45")) > 0) {
            out.add("Kripto varlık oranı yüksek olduğu için risk exposure skoru arttı.");
        }
        if (summary.largestPositionRatio() != null
                && summary.largestPositionRatio().compareTo(new BigDecimal("0.40")) > 0) {
            out.add("Tek pozisyon yoğunlaşması yüksek.");
        }
        if (breakdown.riskExposureScore() >= 7) {
            out.add("Risk exposure skoru yüksek (" + breakdown.riskExposureScore() + ").");
        }
        if (out.isEmpty()) {
            out.add("Portföy profili dengeli görünüyor. Seviye: " + level.name());
        }
        return List.copyOf(out);
    }
}
