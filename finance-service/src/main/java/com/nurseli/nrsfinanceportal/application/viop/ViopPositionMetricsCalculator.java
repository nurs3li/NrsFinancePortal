package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * finance-service VIOP metrik hesaplayıcı — açık VIOP pozisyonu için PnL, marjin, kaldıraç ve risk maruziyeti metriklerini üretir.
 */
@Component

public class ViopPositionMetricsCalculator {

    private static final int SCALE = 6;

    /**
     * Metrics — VIOP pozisyon metrik DTO'su — PnL, marjin, kaldıraç ve risk maruziyeti alanları.
     */
    public record Metrics(
            BigDecimal effectiveCurrentPrice,
            ViopQuoteCurrency quoteCurrency,
            BigDecimal unrealizedPnlNative,
            BigDecimal unrealizedPnlTry,
            BigDecimal riskExposureNative,
            BigDecimal riskExposureTry,
            BigDecimal marginTry,
            BigDecimal leverage,
            BigDecimal marginRatio,
            BigDecimal pnlToMarginRatio,
            BigDecimal netFinancialEffect,
            Integer daysToExpiry,
            boolean missingFxRate
    ) {}

    /**
     * {@code compute} — Çözümlenmiş piyasa fiyatı, FX kurları ve vadeye kalan gün ile VIOP pozisyon metriklerini TRY cinsinden hesaplar.
     */
    public Metrics compute(ManualViopPosition position, BigDecimal resolvedMarketPrice, LocalDate today) {
        return compute(position, resolvedMarketPrice, today, ViopFxRates.empty());
    }

    /**
     * {@code compute} — Çözümlenmiş piyasa fiyatı, FX kurları ve vadeye kalan gün ile VIOP pozisyon metriklerini TRY cinsinden hesaplar.
     */
    public Metrics compute(
            ManualViopPosition position,
            BigDecimal resolvedMarketPrice,
            LocalDate today,
    ViopFxRates fxRates) {
        if (position == null) {
            return emptyMetrics(null, null, today, position);
        }
        ViopQuoteCurrency quote = ViopContractCurrencyResolver.resolve(position);
        BigDecimal current = resolvedMarketPrice != null ? resolvedMarketPrice : position.getCurrentPrice();
        BigDecimal effectiveDisplay = current != null ? current : position.getEntryPrice();

        Integer daysToExpiry = null;
        if (position.getExpiryDate() != null && today != null) {
            daysToExpiry = (int) ChronoUnit.DAYS.between(today, position.getExpiryDate());
        }

        // API teminatı tek sözleşme başınadır; toplam teminat = tek sözleşme × kontrat adedi.
        BigDecimal perContractMargin = nz(position.getInitialMargin());
        BigDecimal marginCount = nz(position.getContractCount());
        BigDecimal marginTry = perContractMargin.multiply(marginCount).setScale(SCALE, RoundingMode.HALF_UP);
        boolean missingFx = quote != ViopQuoteCurrency.TRY && fxRates.isMissing(quote);

        if (position.getStatus() != ViopPositionStatus.OPEN || current == null) {
            return new Metrics(
                    effectiveDisplay,
                    quote,
                    null,
                    null,
                    null,
                    null,
                    marginTry,
                    null,
                    null,
                    null,
                    position.getStatus() == ViopPositionStatus.OPEN ? marginTry : null,
                    daysToExpiry,
                    missingFx);
        }

        // Çarpan kayıttan değil sembol+kategoriye göre gerçek VİOP sözleşme büyüklüğü.
        BigDecimal mult = ViopContractMultiplierResolver.resolve(position);
        BigDecimal count = position.getContractCount();
        if (mult == null || count == null) {
            return new Metrics(
                    effectiveDisplay,
                    quote,
                    null,
                    null,
                    null,
                    null,
                    marginTry,
                    null,
                    null,
                    null,
                    marginTry,
                    daysToExpiry,
                    missingFx);
        }

        BigDecimal entry = position.getEntryPrice();
        BigDecimal diff = position.getDirection() == ViopDirection.LONG
                ? current.subtract(entry)
                : entry.subtract(current);
        BigDecimal pnlNative = diff.multiply(mult).multiply(count).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal riskNative = current.multiply(mult).multiply(count).setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal pnlTry = toTry(pnlNative, quote, fxRates, missingFx);
        BigDecimal riskTry = toTry(riskNative, quote, fxRates, missingFx);

        BigDecimal leverage = ratio(riskTry, marginTry);
        BigDecimal marginRatio = ratio(marginTry, riskTry);
        BigDecimal pnlToMargin = ratio(pnlTry, marginTry);
        BigDecimal net = marginTry.add(nz(pnlTry)).setScale(SCALE, RoundingMode.HALF_UP);

        return new Metrics(
                current,
                quote,
                pnlNative,
                pnlTry,
                riskNative,
                riskTry,
                marginTry,
                leverage,
                marginRatio,
                pnlToMargin,
                net,
                daysToExpiry,
                missingFx);
    }

    private static Metrics emptyMetrics(
            BigDecimal price, ViopQuoteCurrency quote, LocalDate today, ManualViopPosition position) {
        Integer days = null;
        if (position != null && position.getExpiryDate() != null && today != null) {
            days = (int) ChronoUnit.DAYS.between(today, position.getExpiryDate());
        }
        ViopQuoteCurrency q = quote != null ? quote : ViopQuoteCurrency.TRY;
        return new Metrics(price, q, null, null, null, null, null, null, null, null, null, days, false);
    }

    private static BigDecimal toTry(
            BigDecimal nativeAmount,
            ViopQuoteCurrency quote,
            ViopFxRates fxRates,
            boolean missingFxFlag) {
        if (nativeAmount == null) {
            return null;
        }
        if (quote == ViopQuoteCurrency.TRY) {
            return nativeAmount.setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (missingFxFlag || fxRates.isMissing(quote)) {
            return null;
        }
        BigDecimal rate = fxRates.rateFor(quote);
        return nativeAmount.multiply(rate).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal num, BigDecimal den) {
        if (num == null || den == null || den.signum() == 0) {
            return null;
        }
        return num.divide(den, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
