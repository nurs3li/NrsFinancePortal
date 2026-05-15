package com.nurseli.marketdata.application.deposit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Mevduat getiri kabaca faiz hesabı (tek vade; rollover ayrı faz).
 * {@code withholdingTaxRate}: 0–1 aralığı kesinti oranı (örn. 0,15 = %15); {@code null} vergi yok.
 */
public final class DepositReturnCalculator {

    private DepositReturnCalculator() {}

    public static DepositReturnResult simpleMaturity(
            BigDecimal principal,
            String currency,
            String term,
            LocalDate startDate,
            LocalDate maturityDate,
            BigDecimal annualRatePercent,
            BigDecimal withholdingTaxRate
    ) {
        if (principal == null || principal.signum() <= 0 || annualRatePercent == null || startDate == null || maturityDate == null) {
            throw new IllegalArgumentException("principal, annualRatePercent, startDate, maturityDate required");
        }
        if (maturityDate.isBefore(startDate)) {
            throw new IllegalArgumentException("maturityDate before startDate");
        }
        long days = ChronoUnit.DAYS.between(startDate, maturityDate);
        if (days <= 0) {
            days = 1;
        }
        BigDecimal rateFrac = annualRatePercent.divide(new BigDecimal("100"), 12, RoundingMode.HALF_UP);
        BigDecimal gross = principal
                .multiply(rateFrac)
                .multiply(new BigDecimal(days))
                .divide(new BigDecimal("365"), 8, RoundingMode.HALF_UP);
        BigDecimal tax = withholdingTaxRate == null ? BigDecimal.ZERO : withholdingTaxRate;
        if (tax.signum() < 0 || tax.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("withholdingTaxRate must be between 0 and 1");
        }
        BigDecimal net = gross.multiply(BigDecimal.ONE.subtract(tax)).setScale(8, RoundingMode.HALF_UP);
        BigDecimal maturityAmount = principal.add(net).setScale(8, RoundingMode.HALF_UP);
        return new DepositReturnResult(
                principal,
                currency,
                term,
                startDate,
                maturityDate,
                annualRatePercent,
                withholdingTaxRate,
                days,
                gross.setScale(8, RoundingMode.HALF_UP),
                net,
                maturityAmount
        );
    }

    /**
     * Çoklu döneme bölme / otomatik yenileme — ileri faz; şimdilik desteklenmiyor.
     */
    public static DepositReturnResult rolloverStub() {
        throw new UnsupportedOperationException("rollover calculation not implemented in phase 1");
    }

    public record DepositReturnResult(
            BigDecimal principal,
            String currency,
            String term,
            LocalDate startDate,
            LocalDate maturityDate,
            BigDecimal annualRatePercent,
            BigDecimal withholdingTaxRate,
            long dayCount,
            BigDecimal grossInterest,
            BigDecimal netInterest,
            BigDecimal maturityAmount
    ) {}
}
