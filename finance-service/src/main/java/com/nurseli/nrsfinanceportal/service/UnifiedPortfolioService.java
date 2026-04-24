package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioAsset;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.repository.PortfolioAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnifiedPortfolioService {

    private final CurrentUserResolver currentUserResolver;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final ManualPortfolioPositionRepository manualRepo;

    @Transactional(readOnly = true)
    public List<UnifiedPortfolioItemView> myUnifiedPortfolio() {
        return unifiedForUser(currentUserResolver.getOrCreateCurrentUser());
    }

    @Transactional(readOnly = true)
    public List<UnifiedPortfolioItemView> unifiedForUser(User user) {

        List<UnifiedPortfolioItemView> result = new ArrayList<>();

        List<PortfolioAsset> tradeAssets = portfolioAssetRepository.findByUser(user);
        for (PortfolioAsset a : tradeAssets) {
            result.add(new UnifiedPortfolioItemView(
                    "TRADE",
                    a.getType().name(),
                    a.getSymbol(),
                    a.getQuantity(),
                    a.getAvgBuyPrice(),
                    null,
                    null,
                    null
            ));
        }

        List<ManualPortfolioPosition> manualAssets = manualRepo.findByUserIdOrderByBuyDateAsc(user.getId());
        for (ManualPortfolioPosition m : manualAssets) {
            result.add(new UnifiedPortfolioItemView(
                    "MANUAL",
                    m.getType().name(),
                    m.getSymbol(),
                    m.getQuantity(),
                    m.getBuyPrice(),
                    m.getId(),
                    m.getBuyDate(),
                    m.getNote()
            ));
        }

        result.sort(Comparator
                .comparing(UnifiedPortfolioItemView::getType)
                .thenComparing(UnifiedPortfolioItemView::getSymbol)
                .thenComparing(UnifiedPortfolioItemView::getSource));

        return result;
    }
}