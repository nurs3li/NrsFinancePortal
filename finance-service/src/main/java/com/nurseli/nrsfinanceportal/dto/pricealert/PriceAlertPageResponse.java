package com.nurseli.nrsfinanceportal.dto.pricealert;

import java.util.List;

public record PriceAlertPageResponse(
        List<PriceAlertDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
}
