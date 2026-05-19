package com.nurseli.nrsfinanceportal.service.pricealert;

import com.nurseli.nrsfinanceportal.config.PriceAlertProperties;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertStatus;
import com.nurseli.nrsfinanceportal.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertEvaluationJob {

    private final PriceAlertProperties properties;
    private final PriceAlertRepository priceAlertRepository;
    private final PriceAlertMarketSnapshotService snapshotService;
    private final PriceAlertEvaluator evaluator;
    private final PriceAlertNotificationPublisher notificationPublisher;

    @Scheduled(cron = "${app.price-alerts.evaluation-cron:0 */2 * * * *}", zone = "Europe/Istanbul")
    @Transactional
    public void evaluateActiveAlerts() {
        if (!properties.isEnabled()) {
            return;
        }

        List<PriceAlert> active = priceAlertRepository.findByStatusWithUser(PriceAlertStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }

        Set<PriceAlertMarketSnapshotService.AssetKey> keys = new HashSet<>();
        for (PriceAlert alert : active) {
            keys.add(new PriceAlertMarketSnapshotService.AssetKey(alert.getAssetType(), alert.getSymbol()));
        }

        Map<PriceAlertMarketSnapshotService.AssetKey, PriceAlertMarketSnapshot> snapshots =
                snapshotService.buildSnapshots(keys);

        int triggered = 0;
        for (PriceAlert alert : active) {
            var key = new PriceAlertMarketSnapshotService.AssetKey(alert.getAssetType(), alert.getSymbol());
            PriceAlertMarketSnapshot snapshot = snapshots.getOrDefault(key, PriceAlertMarketSnapshot.unavailable());
            if (!evaluator.isTriggered(alert, snapshot)) {
                continue;
            }
            notificationPublisher.publish(alert, snapshot);
            alert.setLastTriggeredAt(Instant.now());
            if (!alert.isRepeatAlert()) {
                alert.setStatus(PriceAlertStatus.TRIGGERED);
            }
            priceAlertRepository.save(alert);
            triggered++;
            log.info("[PRICE_ALERT] Triggered id={} {} {} condition={}",
                    alert.getId(), alert.getAssetType(), alert.getSymbol(), alert.getConditionType());
        }

        if (triggered > 0) {
            log.info("[PRICE_ALERT] Evaluation complete triggered={} active={}", triggered, active.size());
        }
    }
}
