package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.repository.ManualPortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPortfolioService {

    private final ManualPortfolioPositionRepository manualRepo;
    private final CurrentUserResolver currentUserResolver;
    private final PortfolioSnapshotRecorder portfolioSnapshotRecorder;

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

        ManualPortfolioPosition saved = manualRepo.save(p);
        recordManualSnapshot(user.getId());
        return saved;
    }

    @Transactional
    public ManualPortfolioPosition update(Long id, ManualPortfolioCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));

        String normalized = SymbolNormalizer.normalize(
                request.getType(),
                request.getSymbol().trim().toUpperCase()
        );

        p.update(
                request.getType(),
                normalized,
                request.getQuantity(),
                request.getBuyPrice(),
                request.getBuyDate(),
                request.getNote()
        );
        ManualPortfolioPosition saved = manualRepo.save(p);
        recordManualSnapshot(user.getId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ManualPortfolioPosition p = manualRepo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Manuel pozisyon bulunamadi"));
        manualRepo.delete(p);
        recordManualSnapshot(user.getId());
    }

    @Transactional(readOnly = true)
    public List<ManualPortfolioPosition> listMine() {
        Long userId = currentUserResolver.getCurrentUserId();
        return manualRepo.findByUserIdOrderByBuyDateAsc(userId);
    }

    private void recordManualSnapshot(Long userId) {
        try {
            portfolioSnapshotRecorder.record(userId, SnapshotTriggerType.MANUAL, null);
        } catch (Exception e) {
            log.warn("[PORTFOLIO_SNAPSHOT] manual snapshot failed user={}: {}", userId, e.getMessage());
        }
    }
}