package com.nurseli.marketdata.application.loan;

import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.domain.loan.LoanRateSubtype;

import java.util.Arrays;
import java.util.List;

/**
 * EVDS kredi faizi (akım, haftalık) seri tanımları — TP_* kodları YAML/env üzerinden gelir.
 */
public final class LoanRateCatalog {

    private LoanRateCatalog() {}

    public record LoanRateSeriesSpec(
            LoanRateSubtype subType,
            String evdsLogicalSeriesKey,
            String label,
            String description
    ) {}

    public static List<LoanRateSeriesSpec> allSpecs() {
        return List.of(
                new LoanRateSeriesSpec(
                        LoanRateSubtype.CONSUMER_TRY,
                        EvdsSeriesLogicalNames.LOAN_RATE_CONSUMER_TRY_WEEKLY,
                        "İhtiyaç Kredisi Faizi",
                        "Yeni açılan TL ihtiyaç kredilerine uygulanan ağırlıklı ortalama faiz oranı."
                ),
                new LoanRateSeriesSpec(
                        LoanRateSubtype.VEHICLE_TRY,
                        EvdsSeriesLogicalNames.LOAN_RATE_VEHICLE_TRY_WEEKLY,
                        "Taşıt Kredisi Faizi",
                        "Yeni açılan TL taşıt kredilerine uygulanan ağırlıklı ortalama faiz oranı."
                ),
                new LoanRateSeriesSpec(
                        LoanRateSubtype.HOUSING_TRY,
                        EvdsSeriesLogicalNames.LOAN_RATE_HOUSING_TRY_WEEKLY,
                        "Konut Kredisi Faizi",
                        "Yeni açılan TL konut kredilerine uygulanan ağırlıklı ortalama faiz oranı."
                ),
                new LoanRateSeriesSpec(
                        LoanRateSubtype.COMMERCIAL_TRY,
                        EvdsSeriesLogicalNames.LOAN_RATE_COMMERCIAL_TRY_WEEKLY,
                        "Ticari Kredi Faizi",
                        "Yeni açılan TL ticari kredilere uygulanan ağırlıklı ortalama faiz oranı."
                )
        );
    }

    public static LoanRateSeriesSpec specFor(LoanRateSubtype subType) {
        return allSpecs().stream()
                .filter(s -> s.subType() == subType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown subtype: " + subType));
    }

    public static List<LoanRateSubtype> orderedSubtypes() {
        return Arrays.asList(LoanRateSubtype.values());
    }
}
