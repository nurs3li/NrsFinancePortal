package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Varlık yorumları için: en büyük + ağırlık top6 + kazanç top2 + kayıp top2 (maks. 10 sembol).
 */
public final class PortfolioAiPositionSelector {

    public static final int MAX_ASSET_TARGETS = 10;
    private static final int TOP_WEIGHT = 6;
    private static final int TOP_GAINERS = 2;
    private static final int TOP_LOSERS = 2;

    private PortfolioAiPositionSelector() {
    }

    public static List<PortfolioAiContextSnapshot.PositionLine> mergeAssetCommentTargets(
            List<PortfolioAiContextSnapshot.PositionLine> openLinesSortedByWeight,
            List<PortfolioAiContextSnapshot.PositionLine> topGainers,
            List<PortfolioAiContextSnapshot.PositionLine> topLosers,
            String largestPositionSymbol,
            @SuppressWarnings("unused") com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel concentrationLevel
    ) {
        Map<String, PortfolioAiContextSnapshot.PositionLine> merged = new LinkedHashMap<>();

        String largest = largestPositionSymbol != null
                ? largestPositionSymbol.trim().toUpperCase(Locale.ROOT)
                : null;
        if (largest != null) {
            openLinesSortedByWeight.stream()
                    .filter(line -> largest.equals(line.symbol().trim().toUpperCase(Locale.ROOT)))
                    .findFirst()
                    .ifPresent(line -> put(merged, line));
        }

        openLinesSortedByWeight.stream().limit(TOP_WEIGHT).forEach(line -> put(merged, line));
        topGainers.stream().limit(TOP_GAINERS).forEach(line -> put(merged, line));
        topLosers.stream().limit(TOP_LOSERS).forEach(line -> put(merged, line));

        List<PortfolioAiContextSnapshot.PositionLine> result = new ArrayList<>(merged.values());
        if (result.size() > MAX_ASSET_TARGETS) {
            return new ArrayList<>(result.subList(0, MAX_ASSET_TARGETS));
        }
        return result;
    }

    private static void put(
            Map<String, PortfolioAiContextSnapshot.PositionLine> merged,
            PortfolioAiContextSnapshot.PositionLine line
    ) {
        if (line == null || line.symbol() == null || line.symbol().isBlank()) {
            return;
        }
        if (merged.size() >= MAX_ASSET_TARGETS) {
            return;
        }
        merged.putIfAbsent(line.symbol().trim().toUpperCase(Locale.ROOT), line);
    }
}
