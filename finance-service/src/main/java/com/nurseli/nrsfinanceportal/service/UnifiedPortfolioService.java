package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.ManualPortfolioPositionRepository;
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
    private final ManualPortfolioPositionRepository manualRepo;

    @Transactional(readOnly = true)
    public List<UnifiedPortfolioItemView> myUnifiedPortfolio() {
        return unifiedForUser(currentUserResolver.getOrCreateCurrentUser());
    }

    @Transactional(readOnly = true)
    public List<UnifiedPortfolioItemView> unifiedForUser(User user) {

        List<UnifiedPortfolioItemView> result = new ArrayList<>();

        List<ManualPortfolioPosition> manualAssets = manualRepo.findByUser_IdAndStatusOrderByBuyDateAsc(
                user.getId(), ManualPositionStatus.OPEN);
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
