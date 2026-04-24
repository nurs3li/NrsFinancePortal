package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.PortfolioSnapshotPointDto;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioValueSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.repository.PortfolioValueSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioSnapshotQueryService {

    private final CurrentUserResolver currentUserResolver;
    private final PortfolioValueSnapshotRepository snapshotRepository;

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
                s.getTradeId(),
                s.getCombinedValueTry(),
                s.getCombinedCostTry(),
                s.getCombinedPnlTry(),
                s.getTradeValueTry(),
                s.getTradeCostTry(),
                s.getTradePnlTry(),
                s.getManualValueTry(),
                s.getManualCostTry(),
                s.getManualPnlTry()
        );
    }
}
