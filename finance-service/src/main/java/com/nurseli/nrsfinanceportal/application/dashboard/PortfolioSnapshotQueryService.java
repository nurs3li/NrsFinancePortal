package com.nurseli.nrsfinanceportal.application.dashboard;

import com.nurseli.nrsfinanceportal.api.dto.PortfolioSnapshotPointDto;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioValueSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.application.user.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PortfolioValueSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * finance-service portfolio snapshot sorgu servisi — kullanıcının geçmiş portfolio snapshot noktalarını listeler.
 */
@RequiredArgsConstructor
@Service

public class PortfolioSnapshotQueryService {

    private final CurrentUserResolver currentUserResolver;
    private final PortfolioValueSnapshotRepository snapshotRepository;

    /**
     * {@code mySnapshots} — Belirtilen zaman aralığında (isteğe bağlı trigger filtresiyle) kullanıcının snapshot geçmişini döner.
     */
    @Transactional(readOnly = true)
    public List<PortfolioSnapshotPointDto> mySnapshots(Instant from, Instant to, SnapshotTriggerType triggerOrNull) {
        var user = currentUserResolver.getOrCreateCurrentUser();
        List<PortfolioValueSnapshot> rows =
                snapshotRepository.findByUser_IdAndSnapshotAtBetweenOrderBySnapshotAtAsc(user.getId(), from, to);
        if (triggerOrNull == null) {
            return rows.stream().map(this::toDto).toList();
        }
        return rows.stream()
                .filter(r -> r.getTriggerType() == triggerOrNull)
                .map(this::toDto)
                .toList();
    }

    private PortfolioSnapshotPointDto toDto(PortfolioValueSnapshot s) {
        return new PortfolioSnapshotPointDto(
                s.getId(),
                s.getSnapshotAt(),
                s.getTriggerType().name(),
                s.getPortfolioValueTry(),
                s.getPortfolioCostTry(),
                s.getPortfolioPnlTry()
        );
    }
}
