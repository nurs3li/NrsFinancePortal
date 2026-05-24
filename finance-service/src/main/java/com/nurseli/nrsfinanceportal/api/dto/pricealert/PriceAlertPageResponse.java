package com.nurseli.nrsfinanceportal.api.dto.pricealert;

import java.util.List;

/**
 * Fiyat alarmları sayfalı liste response'u; alarm kayıtları ve sayfalama meta verisini taşır.
 */
public record PriceAlertPageResponse(
        List<PriceAlertDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
}
