package com.nurseli.nrsfinanceportal.api.dto.simulation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kayıtlı simülasyon sonuç satırı; liste yanıtlarında series alanı gönderilmez.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationHistoryItemDto(
        String id,
        String assetName,
        String assetType,
        String pickerAssetType,
        String displayCurrency,
        BigDecimal unitsBought,
        BigDecimal initialAmount,
        BigDecimal buyPrice,
        String buyDate,
        BigDecimal currentPrice,
        BigDecimal pnl,
        BigDecimal pnlPct,
        BigDecimal currentValue,
        String buyPriceSource,
        String historicalPriceDate,
        String qualityFlag,
        List<SimulationHistoryPerformancePointDto> series,
        boolean visible,
        String message,
        String approximationNoticeCode,
        String scenarioLabel
) {
}
