package com.nurseli.nrsfinanceportal.dto.pricealert;

import java.math.BigDecimal;

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
