package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ManualPortfolioViewAssembler {

    private final MarketDataClient marketDataClient;
    private final ManualPortfolioCpiSupport cpiSupport;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;
    private final ManualPortfolioRealReturnCalculator realReturnCalculator;

    public List<ManualPortfolioView> toViews(List<ManualPortfolioPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        CpiIndexLookup cpi = cpiSupport.loadForPositions(positions);
        LatestPricingSnapshot pricing = marketDataClient.loadLatestPricing();
        ManualPortfolioRealReturnCalculator.PortfolioRealReturnResult realResult =
                realReturnCalculator.compute(positions, cpi, pricing);

        List<ManualPortfolioView> views = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            ManualPortfolioPosition p = positions.get(i);
            var nominal = nominalAnalysisCalculator.compute(p);
            var positionReal = i < realResult.positions().size()
                    ? realResult.positions().get(i)
                    : null;
            views.add(ManualPortfolioView.from(p, nominal, positionReal));
        }
        return views;
    }

    public ManualPortfolioView toView(ManualPortfolioPosition position) {
        return toViews(List.of(position)).getFirst();
    }
}
