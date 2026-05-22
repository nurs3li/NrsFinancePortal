package com.nurseli.nrsfinanceportal.service.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ViopPositionCloseCalculator {

    private static final int SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ViopPositionCloseCalculator() {
    }

    public record Result(BigDecimal grossPnl, BigDecimal netPnl, BigDecimal returnPercent) {}

    public static Result compute(ManualViopPosition position, BigDecimal closePrice, BigDecimal fee) {
        BigDecimal safeFee = fee != null && fee.signum() > 0 ? fee : BigDecimal.ZERO;
        BigDecimal entry = position.getEntryPrice();
        BigDecimal mult = position.getContractMultiplier();
        BigDecimal count = position.getContractCount();
        if (entry == null || mult == null || count == null || closePrice == null) {
            return new Result(null, null, null);
        }
        BigDecimal diff = position.getDirection() == ViopDirection.LONG
                ? closePrice.subtract(entry)
                : entry.subtract(closePrice);
        BigDecimal gross = diff.multiply(mult).multiply(count).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(safeFee).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal margin = position.getInitialMargin();
        BigDecimal returnPct = null;
        if (margin != null && margin.signum() > 0) {
            returnPct = net.multiply(HUNDRED).divide(margin, 4, RoundingMode.HALF_UP);
        }
        return new Result(gross, net, returnPct);
    }
}
