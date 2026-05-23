package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiDetailLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiRiskProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioAiFallbackGeneratorTest {

    private final PortfolioAiFallbackGenerator generator = new PortfolioAiFallbackGenerator();

    @Test
    void fallbackAssetCommentsAreSubstantive() {
        PortfolioAiContextSnapshot ctx = ctxWithLine("VOO", 42, 8.0, "FUND");
        PortfolioAiParsedOutput out = generator.build(ctx);
        assertThat(out.assetComments()).isNotEmpty();
        String detail = out.assetComments().getFirst().detailComment().toLowerCase();
        assertThat(detail).doesNotContain(PortfolioAiAdviceSanitizer.MECHANICAL_PHRASE);
        assertThat(detail).contains("voo");
    }

    @Test
    void realReturnPositiveSummaryMentioned() {
        PortfolioAiContextSnapshot ctx = new PortfolioAiContextSnapshot(
                PortfolioAiAnalysisType.GENERAL_REVIEW,
                PortfolioAiRiskProfile.BALANCED,
                PortfolioAiDetailLevel.DETAILED,
                false,
                false,
                true,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ONE,
                5.0,
                BigDecimal.ONE,
                4.0,
                true,
                70,
                "VOO",
                40.0,
                PortfolioAiConcentrationLevel.HIGH,
                Map.of("FUND", 40.0),
                List.of(),
                List.of(),
                List.of(),
                List.of(line("VOO", 40, 5)),
                1,
                0,
                Map.of(),
                List.of(),
                false
        );
        PortfolioAiParsedOutput out = generator.build(ctx);
        assertThat(out.summary().toLowerCase()).contains("reel");
    }

    private PortfolioAiContextSnapshot ctxWithLine(String sym, double weight, double ret, String assetClass) {
        return new PortfolioAiContextSnapshot(
                PortfolioAiAnalysisType.GENERAL_REVIEW,
                PortfolioAiRiskProfile.BALANCED,
                PortfolioAiDetailLevel.DETAILED,
                false,
                false,
                true,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                5.0,
                BigDecimal.ZERO,
                3.0,
                true,
                70,
                sym,
                weight,
                PortfolioAiConcentrationLevel.MEDIUM,
                Map.of(assetClass, weight),
                List.of(line(sym, weight, ret)),
                List.of(),
                List.of(),
                List.of(line(sym, weight, ret)),
                1,
                0,
                Map.of(),
                List.of(),
                false
        );
    }

    private static PortfolioAiContextSnapshot.PositionLine line(String sym, double weight, double ret) {
        return new PortfolioAiContextSnapshot.PositionLine(
                sym, sym, "FUND", "OPEN", 1, 100.0, 1000, 90.0, 900.0, 100.0, ret, weight, 30
        );
    }
}
