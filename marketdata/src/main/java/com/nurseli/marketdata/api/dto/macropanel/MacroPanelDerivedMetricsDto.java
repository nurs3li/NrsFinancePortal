package com.nurseli.marketdata.api.dto.macropanel;

/**
 * Endeks ve nominal oranlardan türetilen göstergeler; veri eksikse alanlar {@code null}.
 */
public record MacroPanelDerivedMetricsDto(
        Double cpiMoM,
        Double cpiYoY,
        Double ppiMoM,
        Double ppiYoY,
        Double realPolicyRate,
        Double realDepositRate,
        Double consumerLoanMinusPolicyRate,
        Double consumerLoanMinusDepositRate
) {
    public static MacroPanelDerivedMetricsDto empty() {
        return new MacroPanelDerivedMetricsDto(null, null, null, null, null, null, null, null);
    }
}
