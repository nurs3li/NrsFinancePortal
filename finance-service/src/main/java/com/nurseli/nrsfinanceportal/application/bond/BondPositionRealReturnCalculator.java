package com.nurseli.nrsfinanceportal.application.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondPositionStatus;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * finance-service bond reel getiri hesaplayıcı — Fisher formülüyle dönemsel nominal getiriyi TÜFE ile reel getiriye çevirir.
 */
@Component

public class BondPositionRealReturnCalculator {

    private static final int SCALE = 8;
    private static final int PCT_SCALE = 4;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /**
     * RealReturnFields — Bond reel getiri alanları — dönemsel enflasyon, nominal ve reel getiri değerlerini taşır.
     */
    public record RealReturnFields(
            LocalDate cpiAtPurchaseMonth,
            LocalDate cpiCurrentMonth,
            BigDecimal cpiAtPurchaseIndex,
            BigDecimal cpiCurrentIndex,
            BigDecimal periodInflation,
            BigDecimal periodInflationPercent,
            BigDecimal periodNominalReturn,
            BigDecimal periodNominalReturnPercent,
            BigDecimal periodRealReturn,
            BigDecimal periodRealReturnPercent,
            boolean available
    ) {}

    /**
     * {@code compute} — Alış ve güncel/satış CPI endeksleriyle dönemsel enflasyon, nominal ve reel getiri alanlarını hesaplar.
     */
    public RealReturnFields compute(
            ManualBondPosition position,
            LocalDate asOf,
    BigDecimal buyValue,
            BigDecimal totalReturn,
            CpiIndexLookup cpi) {
        if (position == null || position.getBuyDate() == null || cpi == null || !cpi.isAvailable()) {
            return unavailable();
        }
        if (buyValue == null || buyValue.signum() <= 0) {
            return unavailable();
        }

        LocalDate endDate = position.getStatus() == BondPositionStatus.SOLD && position.getSellDate() != null
                ? position.getSellDate()
                : (asOf != null ? asOf : LocalDate.now());

        Optional<LocalDate> purchaseMonth = cpi.monthAtOrBefore(position.getBuyDate());
        Optional<BigDecimal> buyCpi = purchaseMonth.flatMap(m -> cpi.indexAtOrBefore(position.getBuyDate()));

        Optional<LocalDate> currentMonth;
        Optional<BigDecimal> currentCpi;
        if (position.getStatus() == BondPositionStatus.OPEN) {
            currentMonth = cpi.latestMonth();
            currentCpi = cpi.latestIndex();
        } else {
            currentMonth = cpi.monthAtOrBefore(endDate);
            currentCpi = currentMonth.flatMap(m -> cpi.indexAtOrBefore(endDate));
        }

        if (purchaseMonth.isEmpty() || buyCpi.isEmpty() || currentMonth.isEmpty() || currentCpi.isEmpty()) {
            return unavailable();
        }

        BigDecimal inflationFactor = currentCpi.get().divide(buyCpi.get(), SCALE, RoundingMode.HALF_UP);
        BigDecimal periodInflation = inflationFactor.subtract(ONE);
        BigDecimal periodInflationPercent = periodInflation.multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalRet = totalReturn != null ? totalReturn : BigDecimal.ZERO;
        BigDecimal periodNominalReturn = totalRet.divide(buyValue, SCALE, RoundingMode.HALF_UP);
        BigDecimal periodNominalReturnPercent = periodNominalReturn.multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);

        BigDecimal onePlusNom = ONE.add(periodNominalReturn);
        BigDecimal onePlusInf = ONE.add(periodInflation);
        if (onePlusInf.signum() <= 0) {
            return unavailable();
        }
        BigDecimal periodRealReturn = onePlusNom.divide(onePlusInf, SCALE, RoundingMode.HALF_UP).subtract(ONE);
        BigDecimal periodRealReturnPercent = periodRealReturn.multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);

        return new RealReturnFields(
                purchaseMonth.get(),
                currentMonth.get(),
                buyCpi.get(),
                currentCpi.get(),
                periodInflation,
                periodInflationPercent,
                periodNominalReturn,
                periodNominalReturnPercent,
                periodRealReturn,
                periodRealReturnPercent,
                true
        );
    }

    private static RealReturnFields unavailable() {
        return new RealReturnFields(
                null, null, null, null,
                null, null, null, null, null, null,
                false
        );
    }
}
