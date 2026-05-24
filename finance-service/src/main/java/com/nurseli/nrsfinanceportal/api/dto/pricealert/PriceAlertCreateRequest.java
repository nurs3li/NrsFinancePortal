package com.nurseli.nrsfinanceportal.api.dto.pricealert;

import java.math.BigDecimal;

/**
 * Fiyat alarmı oluşturma request'i; varlık, eşik koşulu ve bildirim kanallarını taşır.
 */
public record PriceAlertCreateRequest(
        String assetType,
        String symbol,
        String conditionType,
        BigDecimal threshold,
        String changeWindow,
        String channels,
        Boolean repeatAlert,
        Integer cooldownHours
) {}
