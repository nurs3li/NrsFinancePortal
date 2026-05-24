package com.nurseli.nrsfinanceportal.api.dto.pricealert;

import java.math.BigDecimal;

/**
 * Fiyat alarmı güncelleme request'i; koşul, eşik, kanal ve durum alanlarını taşır.
 */
public record PriceAlertUpdateRequest(
        String conditionType,
        BigDecimal threshold,
        String changeWindow,
        String channels,
        Boolean repeatAlert,
        Integer cooldownHours,
        String status
) {}
