package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricing.PriceLookupService;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.PortfolioAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortfolioAggregationService {

    private final PortfolioAssetRepository repository;
    private final PriceLookupService priceLookupService;

    public PortfolioSummary aggregate(User user) {

        List<PortfolioAsset> assets =
                repository.findByUser(user);

        BigDecimal totalTry = BigDecimal.ZERO;

        Map<AssetType, BigDecimal> distribution =
                new EnumMap<>(AssetType.class);

        for (PortfolioAsset asset : assets) {

            BigDecimal tryPrice =
                    priceLookupService.getTryPrice(
                            asset.getType(),
                            asset.getSymbol()
                    );

            BigDecimal value =
                    tryPrice.multiply(asset.getQuantity());

            totalTry = totalTry.add(value);

            distribution.merge(
                    asset.getType(),
                    value,
                    BigDecimal::add
            );
        }

        return new PortfolioSummary(totalTry, distribution);
    }
}
