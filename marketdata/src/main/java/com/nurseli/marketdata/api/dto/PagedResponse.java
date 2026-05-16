package com.nurseli.marketdata.api.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int safeSize = Math.max(1, size);
        int totalPages = totalElements <= 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int safePage = Math.max(0, page);
        return new PagedResponse<>(
                content,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                safePage + 1 < totalPages,
                safePage > 0);
    }
}
