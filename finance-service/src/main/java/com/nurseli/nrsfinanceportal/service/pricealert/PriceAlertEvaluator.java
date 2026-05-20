package com.nurseli.nrsfinanceportal.service.pricealert;

import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertConditionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class PriceAlertEvaluator {

    public boolean isTriggered(PriceAlert alert, PriceAlertMarketSnapshot snapshot) {
        if (alert == null || snapshot == null || !snapshot.dataAvailable()) {
            return false;
        }
        if (isInCooldown(alert)) {
            return false;
        }

        return switch (alert.getConditionType()) {
            case PRICE_GTE -> comparePrice(snapshot.priceTry(), alert.getThreshold()) >= 0;
            case PRICE_LTE -> comparePrice(snapshot.priceTry(), alert.getThreshold()) <= 0;
            case CHANGE_PCT_GTE -> compareChange(snapshot.changePct(), alert.getThreshold()) >= 0;
            case CHANGE_PCT_LTE -> compareChange(snapshot.changePct(), alert.getThreshold()) <= 0;
        };
    }

    public boolean isInCooldown(PriceAlert alert) {
        if (alert.getLastTriggeredAt() == null) {
            return false;
        }
        if (!alert.isRepeatAlert()) {
            return true;
        }
        int hours = Math.max(0, alert.getCooldownHours());
        if (hours == 0) {
            return false;
        }
        Instant until = alert.getLastTriggeredAt().plus(hours, ChronoUnit.HOURS);
        return Instant.now().isBefore(until);
    }

    private static int comparePrice(BigDecimal price, BigDecimal threshold) {
        if (price == null || threshold == null) {
            return -1;
        }
        return price.compareTo(threshold);
    }

    private static int compareChange(BigDecimal changePct, BigDecimal threshold) {
        if (changePct == null || threshold == null) {
            return -1;
        }
        return changePct.compareTo(threshold);
    }
}
