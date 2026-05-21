package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioAiPositionSelectorTest {

    @Test
    void mergesWeightGainersLosersAndRiskyWithoutDuplicates() {
        List<PortfolioAiContextSnapshot.PositionLine> open = List.of(
                line("VOO", 64, 10),
                line("AMZN", 25, 14),
                line("GLD", 7, 33),
                line("SILVER", 1, -10),
                line("BTC", 1, 5)
        );
        List<PortfolioAiContextSnapshot.PositionLine> gainers = List.of(
                line("GLD", 7, 33),
                line("AMZN", 25, 14),
                line("BTC", 1, 5)
        );
        List<PortfolioAiContextSnapshot.PositionLine> losers = List.of(
                line("SILVER", 1, -10),
                line("VOO", 64, 10),
                line("AMZN", 25, 14)
        );

        List<PortfolioAiContextSnapshot.PositionLine> merged = PortfolioAiPositionSelector.mergeAssetCommentTargets(
                open,
                gainers,
                losers,
                "VOO",
                PortfolioAiConcentrationLevel.HIGH
        );

        assertThat(merged).extracting(PortfolioAiContextSnapshot.PositionLine::symbol)
                .contains("VOO", "AMZN", "GLD", "SILVER", "BTC")
                .doesNotHaveDuplicates();
        assertThat(merged.size()).isGreaterThanOrEqualTo(5);
        assertThat(merged.size()).isLessThanOrEqualTo(PortfolioAiPositionSelector.MAX_ASSET_TARGETS);
    }

    @Test
    void capsAtTenSymbols() {
        List<PortfolioAiContextSnapshot.PositionLine> open = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            open.add(line("S" + i, 20 - i, i));
        }
        List<PortfolioAiContextSnapshot.PositionLine> merged = PortfolioAiPositionSelector.mergeAssetCommentTargets(
                open,
                open.subList(0, 2),
                open.subList(13, 15),
                "S0",
                PortfolioAiConcentrationLevel.HIGH
        );
        assertThat(merged).hasSizeLessThanOrEqualTo(PortfolioAiPositionSelector.MAX_ASSET_TARGETS);
        assertThat(merged.size()).isGreaterThan(6);
    }

    @Test
    void includesLargestPositionEvenWhenNotInTopWeightSlice() {
        List<PortfolioAiContextSnapshot.PositionLine> open = List.of(
                line("A", 12, 1),
                line("B", 11, 2),
                line("C", 10, 3),
                line("D", 9, 4),
                line("E", 8, 5),
                line("F", 7, 6),
                line("G", 6, 7),
                line("H", 5, 8),
                line("TINY", 2, -20)
        );
        List<PortfolioAiContextSnapshot.PositionLine> merged = PortfolioAiPositionSelector.mergeAssetCommentTargets(
                open,
                List.of(),
                List.of(line("TINY", 2, -20)),
                "TINY",
                PortfolioAiConcentrationLevel.MEDIUM
        );
        assertThat(merged).extracting(PortfolioAiContextSnapshot.PositionLine::symbol).contains("TINY");
    }

    private static PortfolioAiContextSnapshot.PositionLine line(String sym, double weight, double ret) {
        return new PortfolioAiContextSnapshot.PositionLine(
                sym, sym, "FUND", "OPEN", 1.0, 1.0, 100.0, 1.0, 100.0, 0.0, ret, weight, 30
        );
    }
}
