package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.PortfolioConcentrationRiskDto;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioHealthScoreDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PortfolioHealthScoreService {

    public PortfolioHealthScoreDto evaluate(
            ManualPortfolioRealReturnCalculator.PortfolioRealReturnResult realReturn,
            PortfolioConcentrationRiskDto concentration,
            List<ManualPortfolioPosition> positions,
            Map<AssetType, BigDecimal> openValueByType) {

        int score = 100;
        List<String> factors = new ArrayList<>();

        BigDecimal topPct = concentration.topAssetWeightPct();
        if (topPct != null) {
            if (topPct.compareTo(new BigDecimal("60")) > 0) {
                score -= 25;
                factors.add("En büyük varlık payı %60'ı aşıyor (-25)");
            } else if (topPct.compareTo(new BigDecimal("40")) > 0) {
                score -= 15;
                factors.add("En büyük varlık payı %40'ı aşıyor (-15)");
            }
        }

        if (realReturn.realReturnAvailable() && realReturn.realReturn() != null) {
            if (realReturn.realReturn().compareTo(BigDecimal.ZERO) < 0) {
                score -= 25;
                factors.add("Reel getiri negatif (-25)");
            } else if (realReturn.realReturnPct() != null
                    && realReturn.realReturnPct().compareTo(BigDecimal.ZERO) > 0
                    && realReturn.realReturnPct().compareTo(new BigDecimal("5")) <= 0) {
                score -= 10;
                factors.add("Reel getiri %0–5 aralığında (-10)");
            }
        }

        long openCount = positions.stream()
                .filter(p -> p.getStatus() == ManualPositionStatus.OPEN)
                .count();
        if (openCount == 1) {
            score -= 20;
            factors.add("Yalnızca 1 açık pozisyon (-20)");
        } else if (openCount == 2) {
            score -= 10;
            factors.add("Yalnızca 2 açık pozisyon (-10)");
        }

        BigDecimal openTotal = realReturn.openCurrentValue() != null ? realReturn.openCurrentValue() : BigDecimal.ZERO;
        if (openTotal.signum() > 0 && openValueByType != null) {
            for (Map.Entry<AssetType, BigDecimal> e : openValueByType.entrySet()) {
                BigDecimal share = e.getValue()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(openTotal, 4, RoundingMode.HALF_UP);
                if (share.compareTo(new BigDecimal("80")) > 0) {
                    score -= 10;
                    factors.add("Tek varlık sınıfı (%" + share.setScale(1, RoundingMode.HALF_UP)
                            + " " + e.getKey() + ") (-10)");
                    break;
                }
            }
        }

        score = Math.max(0, Math.min(100, score));

        String level;
        String summary;
        if (score >= 80) {
            level = "GOOD";
            summary = "Portföy sağlık skoru güçlü; dağılım ve reel getiri genel olarak iyi.";
        } else if (score >= 60) {
            level = "MEDIUM";
            summary = "Portföy sağlık skoru orta; bazı risk faktörleri dikkat gerektiriyor.";
        } else {
            level = "WEAK";
            summary = "Portföy sağlık skoru zayıf; konsantrasyon veya reel getiri açısından iyileştirme önerilir.";
        }

        if (factors.isEmpty()) {
            factors.add("Belirgin ceza faktörü yok");
        }

        return new PortfolioHealthScoreDto(score, level, summary, List.copyOf(factors));
    }
}
