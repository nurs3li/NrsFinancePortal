package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioConcentrationRiskDto;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * finance-service portfolio konsantrasyon risk servisi — açık pozisyonların değer ağırlığına göre konsantrasyon riskini değerlendirir.
 */
@RequiredArgsConstructor
@Component

public class PortfolioConcentrationRiskService {

    private static final int PCT_SCALE = 4;

    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;
    private final MarketDataClient marketDataClient;

    /**
     * OpenPositionValue — Açık pozisyon değer satırı — sembol ve TRY piyasa değeri ağırlığı için hesap girdisi.
     */
    public record OpenPositionValue(ManualPortfolioPosition position, BigDecimal currentValue) {}

    /**
     * {@code evaluate} — En büyük varlık ve top-3 ağırlık yüzdelerini hesaplayarak konsantrasyon risk seviyesi ve mesajı üretir.
     */
    public PortfolioConcentrationRiskDto evaluate(
            List<ManualPortfolioPosition> positions,
            LatestPricingSnapshot pricing) {
    List<OpenPositionValue> open = new ArrayList<>();
        for (ManualPortfolioPosition p : positions) {
            if (p.getStatus() != ManualPositionStatus.OPEN) {
                continue;
            }
            ManualPortfolioNominalAnalysis a = nominalAnalysisCalculator.computeWithCurrentPrice(
                    p,
                    marketDataClient.getPriceTry(p.getType(), p.getSymbol(), pricing)
            );
            BigDecimal val = a.currentValue();
            if (val != null && val.signum() > 0) {
                open.add(new OpenPositionValue(p, val));
            }
        }

        if (open.isEmpty()) {
            return new PortfolioConcentrationRiskDto(
                    null,
                    null,
                    null,
                    "LOW",
                    "Açık pozisyon bulunmuyor; konsantrasyon riski değerlendirilemedi."
            );
        }

        BigDecimal total = open.stream()
                .map(OpenPositionValue::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.signum() <= 0) {
            return new PortfolioConcentrationRiskDto(
                    null,
                    null,
                    null,
                    "LOW",
                    "Açık pozisyon değeri hesaplanamadı."
            );
        }

        List<OpenPositionValue> sorted = open.stream()
                .sorted(Comparator.comparing(OpenPositionValue::currentValue).reversed())
                .toList();

        OpenPositionValue top = sorted.get(0);
        BigDecimal topPct = weightPct(top.currentValue(), total);
        BigDecimal top3Sum = sorted.stream()
                .limit(3)
                .map(OpenPositionValue::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal top3Pct = weightPct(top3Sum, total);

        String riskLevel;
        if (topPct.compareTo(new BigDecimal("60")) >= 0) {
            riskLevel = "HIGH";
        } else if (topPct.compareTo(new BigDecimal("40")) >= 0) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        String message = switch (riskLevel) {
            case "HIGH" -> String.format(
                    "Portföyünüzün %%%s oranı %s varlığında yoğunlaşmış; tek varlık riski yüksek.",
                    topPct.setScale(1, RoundingMode.HALF_UP),
                    top.position().getSymbol());
            case "MEDIUM" -> String.format(
                    "En büyük açık pozisyon (%s) portföyün %%%s payını oluşturuyor.",
                    top.position().getSymbol(),
                    topPct.setScale(1, RoundingMode.HALF_UP));
            default -> "Açık pozisyon dağılımı konsantrasyon açısından dengeli görünüyor.";
        };

        return new PortfolioConcentrationRiskDto(
                top.position().getSymbol(),
                topPct,
                top3Pct,
                riskLevel,
                message
        );
    }

    private static BigDecimal weightPct(BigDecimal part, BigDecimal total) {
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, PCT_SCALE, RoundingMode.HALF_UP);
    }
}
