package com.nurseli.marketdata.application.deposit;

import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;

import java.util.List;

/**
 * EVDS mevduat faizi (akım, %) — haftalık seriler; TP_* kodları {@code market.evds.series} / env üzerinden gelir.
 */
public final class DepositRateCatalog {

    private DepositRateCatalog() {}

    public record DepositRateSeriesSpec(String evdsLogicalKey, String currency, String term) {}

    public static List<DepositRateSeriesSpec> allSpecs() {
        return List.of(
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_1M_WEEKLY, "TRY", "1M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_3M_WEEKLY, "TRY", "3M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_6M_WEEKLY, "TRY", "6M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_1Y_WEEKLY, "TRY", "1Y"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_GT1Y_WEEKLY, "TRY", "GT1Y"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_EUR_1M_WEEKLY, "EUR", "1M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_EUR_3M_WEEKLY, "EUR", "3M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_EUR_6M_WEEKLY, "EUR", "6M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_EUR_1Y_WEEKLY, "EUR", "1Y"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_USD_1M_WEEKLY, "USD", "1M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_USD_3M_WEEKLY, "USD", "3M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_USD_6M_WEEKLY, "USD", "6M"),
                new DepositRateSeriesSpec(EvdsSeriesLogicalNames.DEPOSIT_RATE_USD_1Y_WEEKLY, "USD", "1Y"));
    }
}
