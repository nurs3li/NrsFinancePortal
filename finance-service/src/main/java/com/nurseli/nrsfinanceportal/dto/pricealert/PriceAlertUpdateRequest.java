package com.nurseli.nrsfinanceportal.dto.pricealert;

import java.math.BigDecimal;

public record PriceAlertUpdateRequest(
        String conditionType,
        BigDecimal threshold,
        String changeWindow,
        String channels,
        Boolean repeatAlert,
        Integer cooldownHours,
        String status
) {}
