package com.nurseli.nrsfinanceportal.service.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class ViopPositionMetricsCalculator {

    private static final int SCALE = 6;

    public record Metrics(
            BigDecimal effectiveCurrentPrice,
            BigDecimal unrealizedPnl,
            BigDecimal riskExposure,
            BigDecimal netFinancialEffect,
            Integer daysToExpiry
    ) {}

    public Metrics compute(ManualViopPosition position, BigDecimal resolvedMarketPrice, LocalDate today) {
        if (position == null) {
            return new Metrics(null, null, null, null, null);
        }
        BigDecimal current = resolvedMarketPrice != null ? resolvedMarketPrice : position.getCurrentPrice();
        BigDecimal effectiveDisplay = current != null ? current : position.getEntryPrice();

        Integer daysToExpiry = null;
        if (position.getExpiryDate() != null && today != null) {
            daysToExpiry = (int) ChronoUnit.DAYS.between(today, position.getExpiryDate());
        }

        if (position.getStatus() != ViopPositionStatus.OPEN || current == null) {
            BigDecimal margin = nz(position.getInitialMargin());
            return new Metrics(effectiveDisplay, null, null,
                    position.getStatus() == ViopPositionStatus.OPEN ? margin : null, daysToExpiry);
        }

        BigDecimal mult = position.getContractMultiplier();
        BigDecimal count = position.getContractCount();
        if (mult == null || count == null) {
            return new Metrics(effectiveDisplay, null, null, nz(position.getInitialMargin()), daysToExpiry);
        }

        BigDecimal entry = position.getEntryPrice();
        BigDecimal diff = position.getDirection() == ViopDirection.LONG
                ? current.subtract(entry)
                : entry.subtract(current);
        BigDecimal pnl = diff.multiply(mult).multiply(count).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal risk = current.multiply(mult).multiply(count).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal net = nz(position.getInitialMargin()).add(pnl).setScale(SCALE, RoundingMode.HALF_UP);

        return new Metrics(current, pnl, risk, net, daysToExpiry);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
