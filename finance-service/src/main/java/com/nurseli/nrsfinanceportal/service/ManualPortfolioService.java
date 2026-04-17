package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.ManualPortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManualPortfolioService {

    private final ManualPortfolioPositionRepository manualRepo;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public ManualPortfolioPosition create(ManualPortfolioCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();

        String normalized = SymbolNormalizer.normalize(
                request.getType(),
                request.getSymbol().trim().toUpperCase()
        );

        ManualPortfolioPosition p = ManualPortfolioPosition.create(
                user,
                request.getType(),
                normalized,
                request.getQuantity(),
                request.getBuyPrice(),
                request.getBuyDate(),
                request.getNote()
        );

        return manualRepo.save(p);
    }

    @Transactional(readOnly = true)
    public List<ManualPortfolioPosition> listMine() {
        Long userId = currentUserResolver.getCurrentUserId();
        return manualRepo.findByUserIdOrderByBuyDateAsc(userId);
    }
}