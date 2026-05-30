package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mevcut manuel portföy kullanıcıları için materialized read warm-up kuyruğu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "manual.portfolio.backfill.enabled", havingValue = "true", matchIfMissing = true)
public class ManualPortfolioBackfillJob {

    private static final int BATCH_SIZE = 5;

    private final ManualPortfolioPositionRepository positionRepository;
    private final ManualPortfolioWarmupService warmupService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<Long> userIds = positionRepository.findDistinctUserIds();
        if (userIds.isEmpty()) {
            return;
        }
        log.info("[MANUAL_BACKFILL] scheduling warmup for users={}", userIds.size());
        for (int i = 0; i < userIds.size(); i++) {
            warmupService.scheduleWarmup(userIds.get(i));
            if ((i + 1) % BATCH_SIZE == 0) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
