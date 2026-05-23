package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiDetailLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiRiskProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioAiContextBuilderTest {

    private final PortfolioAiContextBuilder builder = new PortfolioAiContextBuilder(
            null,
            null,
            null,
            null,
            new ObjectMapper()
    );

    @Test
    void compactContextDoesNotIncludePlaceholderNewsPerSymbol() throws Exception {
        List<PortfolioAiContextSnapshot.PositionLine> lines = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lines.add(line("S" + i, 5 + i, i));
        }
        List<PortfolioAiContextSnapshot.PositionLine> targets = PortfolioAiPositionSelector.mergeAssetCommentTargets(
                lines,
                lines.subList(0, 2),
                lines.subList(10, 12),
                "S11",
                PortfolioAiConcentrationLevel.HIGH
        );
        assertThat(targets).hasSizeLessThanOrEqualTo(PortfolioAiPositionSelector.MAX_ASSET_TARGETS);

        PortfolioAiContextSnapshot snapshot = snapshot(targets, true);
        String json = builder.toCompactContextJson(snapshot);
        assertThat(json).contains("newsAvailability");
        assertThat(json).contains("NOT_AVAILABLE");
        assertThat(json).doesNotContain("shortNewsSummary");
        assertThat(json).doesNotContain("Haber özeti bu sürümde");
    }

    @Test
    void compactContextOmitsQuantity() throws Exception {
        PortfolioAiContextSnapshot snapshot = snapshot(
                List.of(line("VOO", 50, 10)),
                false
        );
        String json = builder.toCompactContextJson(snapshot);
        assertThat(json).contains("symbol");
        assertThat(json).doesNotContain("quantity");
    }

    private PortfolioAiContextSnapshot snapshot(
            List<PortfolioAiContextSnapshot.PositionLine> targets,
            boolean includeNews
    ) {
        return new PortfolioAiContextSnapshot(
                PortfolioAiAnalysisType.GENERAL_REVIEW,
                PortfolioAiRiskProfile.BALANCED,
                PortfolioAiDetailLevel.DETAILED,
                includeNews,
                false,
                true,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(100),
                11.0,
                BigDecimal.valueOf(50),
                5.0,
                true,
                65,
                "VOO",
                50.0,
                PortfolioAiConcentrationLevel.MEDIUM,
                Map.of("FUND", 50.0),
                targets,
                targets,
                targets,
                targets,
                targets.size(),
                0,
                Map.of(),
                List.of(),
                false
        );
    }

    private static PortfolioAiContextSnapshot.PositionLine line(String sym, double weight, double ret) {
        return new PortfolioAiContextSnapshot.PositionLine(
                sym, sym, "FUND", "OPEN", 99.0, 100.0, 1000, 90.0, 900.0, 100.0, ret, weight, 30
        );
    }
}
